package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.common.utils.PermissionUtil;
import com.zhanghan.sshproxyproject.core.review.StaticRuleEngine;
import com.zhanghan.sshproxyproject.entity.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 命令审查服务 — 静态 + 动态 两级审查编排器
 * <p>
 * 整合 Phase 1（静态规则引擎）和 Phase 2（DeepSeek AI 审查），
 * 统一对外暴露 {@link #review(String, User)} 方法。
 * <p>
 * <b>两级审查流程：</b>
 * <pre>
 * 用户命令
 *   │
 *   ├─ Phase 1: StaticRuleEngine (同步，&lt; 1ms)
 *   │      ├─ BLOCK → 直接返回拦截
 *   │      ├─ ALLOW → 直接返回放行
 *   │      └─ SUSPICIOUS → 先返回放行 + 异步触发 Phase 2
 *   │
 *   └─ Phase 2: DeepSeekService (异步，1~3s)
 *          ├─ HIGH → AlertService.handleHighRiskAlert()
 *          ├─ MEDIUM → AlertService.handleMediumRiskAlert()
 *          └─ LOW/SAFE → 静默忽略
 * </pre>
 * <p>
 * <b>性能设计：</b>
 * <ul>
 *   <li>90%+ 运维命令走 Phase 1 ALLOW 快速通道，零额外延迟</li>
 *   <li>&lt; 5% 灰区命令才触发异步 AI 审查，不阻塞终端交互</li>
 *   <li>AI 调用失败自动降级，不影响命令执行</li>
 * </ul>
 */
@Service
@Slf4j
public class CommandReviewService {

    @Resource
    private StaticRuleEngine staticRuleEngine;

    @Resource
    private DeepSeekService deepSeekService;

    @Resource
    private AlertService alertService;

    // ==================== 公开方法 ====================

    /**
     * 对用户命令进行两级审查
     * <p>
     * <b>返回值含义：</b>
     * <ul>
     *   <li>{@link CommandProcessor#isBlocked()} = true → 必须拦截，不转发</li>
     *   <li>{@link CommandProcessor#isBlocked()} = false → 放行（可能带 needsAiReview 标记）</li>
     *   <li>{@link CommandProcessor#isNeedsAiReview()} = true → 已提交异步 AI 审查</li>
     * </ul>
     *
     * @param command 用户输入的完整命令
     * @param user    当前会话用户（含角色信息）
     * @return 综合审查结果
     */
    public CommandProcessor review(String command, User user) {
        // ---- 防御性检查 ----
        if (command == null || command.isBlank()) {
            return CommandProcessor.allow("空命令");
        }

        // ============================================================
        // Phase 1: 角色权限检查（已有逻辑，保持不变）
        // ============================================================
        boolean permitted = PermissionUtil.checkUserPermission(user, command);
        if (!permitted) {
            log.info("审查: 角色权限不足, user={}, role={}, cmd='{}'", user.getUsername(), user.getRole(), abbreviate(command));
            return CommandProcessor.block(
                    "角色权限不足: " + user.getRole() + " 不允许执行该命令",
                    "角色白名单拦截"
            );
        }

        // ============================================================
        // Phase 1: 静态规则引擎（新增）
        // ============================================================
        StaticReviewResult staticResult = staticRuleEngine.review(command);

        switch (staticResult.getVerdict()) {
            case BLOCK -> {
                // 明确高危 → 直接拦截，不转发
                log.warn("审查: 静态规则拦截, user={}, cmd='{}', rule='{}'",
                        user.getUsername(), abbreviate(command), staticResult.getMatchedRule());
                return CommandProcessor.block(
                        "高危命令被拦截: " + staticResult.getReason(),
                        staticResult.getMatchedRule()
                );
            }

            case SUSPICIOUS -> {
                // 灰区 → 先放行执行，异步提交 AI 二次审查
                log.info("审查: 灰区命令提交异步AI, user={}, cmd='{}', rule='{}'",
                        user.getUsername(), abbreviate(command), staticResult.getMatchedRule());

                // 异步触发 Phase 2（不阻塞返回）
                submitAsyncAiReview(command, user, staticResult);

                return CommandProcessor.suspicious(
                        staticResult.getReason(),
                        staticResult.getMatchedRule()
                );
            }

            case ALLOW -> {
                // 明确安全 → 直接放行
                return CommandProcessor.allow("静态规则通过");
            }

            default -> {
                // 兜底：不应到达此处
                log.error("审查: 未知的静态审查结论: {}", staticResult.getVerdict());
                return CommandProcessor.allow("未知结论，降级放行");
            }
        }
    }

    // ==================== 异步 AI 审查（Phase 2） ====================

    /**
     * 异步提交 AI 审查，不阻塞主线程
     * <p>
     * AI 返回后根据风险等级自动回调 {@link AlertService} 的方法：
     * <ul>
     *   <li>HIGH → {@link AlertService#handleHighRiskAlert}</li>
     *   <li>MEDIUM → {@link AlertService#handleMediumRiskAlert}</li>
     *   <li>LOW / SAFE → 仅日志记录，不做告警</li>
     * </ul>
     * <p>
     * AI 调用失败（网络超时 / 服务不可用等）时自动降级，
     * 不影响用户正在进行的 SSH 会话。
     *
     * @param command      原始命令
     * @param user         执行用户
     * @param staticResult 静态审查结果（含 matchedRule，供 AI 参考上下文）
     */
    @Async("alertExecutor")
    public void submitAsyncAiReview(String command, User user, StaticReviewResult staticResult) {
        // 注意：此方法异步执行，BackendServer / sessionId / userIp 由调用方注入。
        // 对于 ProxyForwarder 场景，需要额外的上下文传递。
        // 此处仅记录 AI 结果，告警动作由 ProxyForwarder 的回调版本完成。
        log.debug("异步 AI 审查启动: cmd='{}', user={}", abbreviate(command), user.getUsername());

        try {
            //发送请求
            AiReviewResult aiResult = deepSeekService.reviewCommand(command, user.getUsername(), user.getRole());

            if (aiResult == null) {
                log.warn("异步 AI 审查返回 null（API 调用失败降级），cmd='{}'", abbreviate(command));
                return;
            }

            log.info("异步 AI 审查完成: cmd='{}', level={}, category={}, reason={}",
                    abbreviate(command), aiResult.getLevel(), aiResult.getCategory(), aiResult.getReason());

        } catch (Exception e) {
            log.error("异步 AI 审查异常（不影响用户操作）, cmd='{}'", abbreviate(command), e);
            // 异常降级：不做任何告警，不抛异常
        }
    }

    /**
     * 异步提交 AI 审查并自动触发告警回调（完整版）
     * <p>
     * 相比 {@link #submitAsyncAiReview}，此方法额外接收会话上下文，
     * AI 审查完成后自动回调 {@link AlertService} 执行对应等级的告警动作。
     *
     * @param command   原始命令
     * @param user      执行用户
     * @param server    目标后端服务器
     * @param sessionId SSH 会话 ID
     * @param userIp    用户客户端 IP
     */
    @Async("alertExecutor")
    public void submitAsyncAiReviewWithAlert(String command,
                                              User user,
                                              BackendServer server,
                                              String sessionId,
                                              String userIp) {
        log.debug("异步 AI 审查启动(含告警回调): cmd='{}', user={}, sessionId={}",
                abbreviate(command), user.getUsername(), sessionId);

        try {
            AiReviewResult aiResult = deepSeekService.reviewCommand(command, user.getUsername(), user.getRole());

            if (aiResult == null) {
                log.warn("异步 AI 审查返回 null（降级放行），cmd='{}'", abbreviate(command));
                return;
            }

            // 根据 AI 风险等级分流告警
            String level = aiResult.getLevel();
            if ("HIGH".equalsIgnoreCase(level)) {
                alertService.handleHighRiskAlert(command, user, server, aiResult, sessionId, userIp);
            } else if ("MEDIUM".equalsIgnoreCase(level)) {
                alertService.handleMediumRiskAlert(command, user, server, aiResult, sessionId, userIp);
            } else {
                // LOW / SAFE → 静默，仅 debug 日志
                log.debug("异步 AI 审查结果安全: cmd='{}', level={}", abbreviate(command), level);
            }

        } catch (Exception e) {
            log.error("异步 AI 审查异常（降级放行）, cmd='{}'", abbreviate(command), e);
        }
    }

    // ==================== 工具方法 ====================

    private static String abbreviate(String cmd) {
        return cmd.length() > 80 ? cmd.substring(0, 80) + "..." : cmd;
    }
}
