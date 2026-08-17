package com.zhanghan.sshproxyproject.core.review;

import com.zhanghan.sshproxyproject.common.Constants;
import com.zhanghan.sshproxyproject.common.Constants.RuleEntry;
import com.zhanghan.sshproxyproject.entity.ReviewVerdict;
import com.zhanghan.sshproxyproject.entity.StaticReviewResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 静态规则引擎 — Phase 1（同步，&lt; 1ms）
 * <p>
 * 使用预编译的正则表达式对用户命令进行快速分类（四层漏斗）：
 * <ul>
 *   <li>命中 {@link Constants#BLOCK_PATTERNS} → {@link ReviewVerdict#BLOCK} — 直接拦截</li>
 *   <li>命中 {@link Constants#ALLOW_PATTERNS} → {@link ReviewVerdict#ALLOW} — 白名单快速放行</li>
 *   <li>命中 {@link Constants#SUSPICIOUS_PATTERNS} → {@link ReviewVerdict#SUSPICIOUS} — 先放行，异步 AI</li>
 *   <li>未命中任何规则 → {@link ReviewVerdict#ALLOW} — 默认放行</li>
 * </ul>
 * <p>
 * <b>设计要点：</b>
 * <ol>
 *   <li>BLOCK 规则优先匹配 — 确认危险立即拦截，不做多余计算</li>
 *   <li>ALLOW 规则次之 — 明确安全的命令快速放行，减少灰区误报</li>
 *   <li>SUSPICIOUS 规则再次 — 命令进入灰区，交由 AI 异步二次确认</li>
 *   <li>都不命中 → 默认安全放行（覆盖 90%+ 正常运维命令）</li>
 *   <li>所有正则均预编译（static final），零运行时编译开销</li>
 * </ol>
 */
@Component
@Slf4j
public class StaticRuleEngine {

    /**
     * 对单条命令执行静态审查
     * <p>
     * 匹配顺序：BLOCK 规则 → SUSPICIOUS 规则 → 默认 ALLOW。
     * 一旦命中 BLOCK 规则立即返回，不再检查 SUSPICIOUS；
     * 一旦命中 SUSPICIOUS 规则立即返回，不再继续遍历。
     *
     * @param command 用户输入的完整命令（已 trim）
     * @return 静态审查结果，包含结论、命中的规则名、原因说明
     */
    public StaticReviewResult review(String command) {
        // ---- 空命令防御 ----
        if (command == null || command.isBlank()) {
            return StaticReviewResult.builder()
                    .verdict(ReviewVerdict.ALLOW)
                    .matchedRule("无")
                    .reason("空命令，直接放行")
                    .needsAiReview(false)
                    .build();
        }

        // ---- 第一轮：BLOCK 规则（明确高危） ----
        // 遍历顺序即为规则优先级，命中第一个立即返回
        for (RuleEntry rule : Constants.BLOCK_PATTERNS) {
            if (rule.matches(command)) {
                log.info("静态审查 BLOCK: cmd='{}', rule='{}'", abbreviate(command), rule.name());
                return StaticReviewResult.builder()
                        .verdict(ReviewVerdict.BLOCK)
                        .matchedRule(rule.name())
                        .reason("命中高危规则: " + rule.name())
                        .needsAiReview(false)   // BLOCK 已定论，无需 AI
                        .build();
            }
        }

        // ---- 第二轮：ALLOW 规则（白名单快速通道） ----
        // 明确安全的命令模式（如 ls -la、systemctl status、docker ps）
        // 命中后直接放行，不进入 SUSPICIOUS 检查，减少灰区误报
        for (RuleEntry rule : Constants.ALLOW_PATTERNS) {
            if (rule.matches(command)) {
                log.debug("静态审查 ALLOW(白名单): cmd='{}', rule='{}'", abbreviate(command), rule.name());
                return StaticReviewResult.builder()
                        .verdict(ReviewVerdict.ALLOW)
                        .matchedRule(rule.name())
                        .reason("命中白名单规则: " + rule.name())
                        .needsAiReview(false)
                        .build();
            }
        }

        // ---- 第三轮：SUSPICIOUS 规则（灰区） ----
        // 这些命令本身可能是合法的（如 curl），但参数组合可疑
        for (RuleEntry rule : Constants.SUSPICIOUS_PATTERNS) {
            if (rule.matches(command)) {
                log.info("静态审查 SUSPICIOUS: cmd='{}', rule='{}'", abbreviate(command), rule.name());
                return StaticReviewResult.builder()
                        .verdict(ReviewVerdict.SUSPICIOUS)
                        .matchedRule(rule.name())
                        .reason("触发可疑规则: " + rule.name() + "，已提交 AI 二次审查")
                        .needsAiReview(true)    // 需要 AI 异步确认
                        .build();
            }
        }

        // ---- 默认：ALLOW（明确安全） ----
        // 未命中任何规则的命令视为安全（ls / cd / cat / df / ps 等常见运维命令）
        return StaticReviewResult.builder()
                .verdict(ReviewVerdict.ALLOW)
                .matchedRule("无")
                .reason("未命中任何规则，放行")
                .needsAiReview(false)
                .build();
    }

    /**
     * 截断过长命令用于日志输出（避免刷屏）
     */
    private static String abbreviate(String cmd) {
        return cmd.length() > 80 ? cmd.substring(0, 80) + "..." : cmd;
    }
}
