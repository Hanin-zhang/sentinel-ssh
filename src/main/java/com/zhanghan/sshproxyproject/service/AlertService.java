package com.zhanghan.sshproxyproject.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhanghan.sshproxyproject.entity.AiReviewResult;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.session.SessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步告警服务 — Phase 2 回调处理
 * <p>
 * 当 AI 异步审查判定命令为 HIGH / MEDIUM 风险时，
 * 由 {@code CommandReviewService} 回调本服务的方法执行以下动作：
 * <ul>
 *   <li><b>HIGH 风险</b>：写入告警日志、SSE 推送管理员、可选强制断开会话</li>
 *   <li><b>MEDIUM 风险</b>：静默记录到告警表，供后续审计分析</li>
 *   <li><b>LOW / SAFE</b>：不做任何告警动作</li>
 * </ul>
 * <p>
 * 所有方法均为异步执行，不阻塞 SSH 代理主线程。
 */
@Service
@Slf4j
public class AlertService {

    @Resource
    private IAuditLogService auditLogService;
    @Resource
    private SessionManager sessionManager;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private SseEmitterRegistry sseEmitterRegistry;
    // ==================== 告警缓存（供 Dashboard 实时查询） ====================

    /**
     * 最近 N 条 HIGH 风险告警，供 Dashboard 实时轮询展示。
     * Key: 告警 ID（自增），Value: 告警详情。
     */
    private static final Map<Long, AlertRecord> RECENT_ALERTS = new ConcurrentHashMap<>();
    private static long alertIdCounter = 0;

    // ==================== 公开方法 ====================

    /**
     * 处理 AI 审查后的 HIGH 风险命令
     * <p>
     * 执行动作：
     * <ol>
     *   <li>写入审计日志（标记为高危）</li>
     *   <li>写入内存告警缓存（供 Dashboard 实时查询）</li>
     *   <li>对于反弹 Shell / 后门类别，发起强制断连（可选配置）</li>
     * </ol>
     * <p>
     * <b>注意：</b>此方法标记了 {@link Async}，调用方不能依赖其返回值。
     *
     * @param command  原始命令
     * @param user     执行用户
     * @param server   目标后端服务器
     * @param aiResult AI 审查结果
     * @param sessionId SSH 会话 ID（用于可能的强制断连）
     * @param userIp   用户客户端 IP
     */
    @Async("alertExecutor")
    public void handleHighRiskAlert(String command,
                                    User user,
                                    BackendServer server,
                                    AiReviewResult aiResult,
                                    String sessionId,
                                    String userIp) {
        log.warn("⚠️ HIGH 风险告警: user={}, cmd='{}', category={}, reason={}, sessionId={}",
                user.getUsername(),
                abbreviate(command),
                aiResult.getCategory(),
                aiResult.getReason(),
                sessionId);

        // 1. 写入高危审计日志（status=2 表示 AI 判定高危）
        auditLogService.saveHighRisk(command, user, server, userIp, aiResult);

        // 2. 写入内存告警缓存
        AlertRecord record = AlertRecord.builder()
                .id(++alertIdCounter)
                .username(user.getUsername())
                .userId(user.getId())
                .command(command)
                .category(aiResult.getCategory())
                .reason(aiResult.getReason())
                .sessionId(sessionId)
                .userIp(userIp)
                .time(LocalDateTime.now())
                .build();
        RECENT_ALERTS.put(record.getId(), record);

        // 2.5 SSE 实时推送管理员（高危告警）
        pushAlert(record);

        // 3. 极端高危类别 → 强制断连
        //    反弹 Shell / 后门持久化 两类场景即使是异步也应立即断连止损
        if ("反弹Shell".equals(aiResult.getCategory()) || "后门".equals(aiResult.getCategory())) {
            log.error("🚨 检测到极端高危操作({})，建议立即断开会话: sessionId={}",
                    aiResult.getCategory(), sessionId);
            //接入 SessionManage.forceDisconnect(sessionId) 强制断连
            sessionManager.forceDisconnect(sessionId);
        }

        // 4. 清理过期告警（保留最近 200 条）
        if (RECENT_ALERTS.size() > 200) {
            RECENT_ALERTS.keySet().stream()
                    .sorted()
                    .limit(RECENT_ALERTS.size() - 200)
                    .forEach(RECENT_ALERTS::remove);
        }
    }

    /**
     * 处理 AI 审查后的 MEDIUM 风险命令
     * <p>
     * 静默写入审计日志，不做实时通知。供后续定期安全审计使用。
     */
    @Async("alertExecutor")
    public void handleMediumRiskAlert(String command,
                                       User user,
                                       BackendServer server,
                                       AiReviewResult aiResult,
                                       String sessionId,
                                       String userIp) {
        log.info("🔶 MEDIUM 风险记录: user={}, cmd='{}', category={}, reason={}",
                user.getUsername(),
                abbreviate(command),
                aiResult.getCategory(),
                aiResult.getReason());

        // 写入审计日志（status=3 表示 AI 判定中危，仅记录不告警）
        auditLogService.saveMediumRisk(command, user, server, userIp, aiResult);
    }

    /**
     * 获取最近 N 条 HIGH 告警（供 Dashboard 调用）
     *
     *
     * @return 按时间倒序的告警列表
     */
    public Map<Long, AlertRecord> getRecentAlerts() {
        return Map.copyOf(RECENT_ALERTS);
    }

    // ==================== 内部类型 ====================

    /**
     * 一条高危告警记录（内存缓存，非持久化）
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertRecord {
        private long id;
        private String username;
        private Long userId;
        private String command;
        private String category;      // AI 分类: 反弹Shell / 数据破坏 / 权限提升 / ...
        private String reason;        // AI 分析原因
        private String sessionId;
        private String userIp;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime time;
    }

    // ==================== 工具方法 ====================

    /**
     * 通过 SSE 向管理员推送一条高危告警
     */
    private void pushAlert(AlertRecord record) {
        try {
            //对象->json字符串
            String json = objectMapper.writeValueAsString(record);
            //广播
            sseEmitterRegistry.broadcast(json);
        } catch (Exception e) {
            log.warn("SSE 告警推送失败: id={}", record.getId(), e);
        }
    }

    private static String abbreviate(String cmd) {
        return cmd.length() > 100 ? cmd.substring(0, 100) + "..." : cmd;
    }


}
