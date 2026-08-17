package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.zhanghan.sshproxyproject.session.SessionManager.DANGERCMD_MAP;

/**
 * AI 策略建议服务
 * <p>
 * 负责"生成"与"读取"安全策略建议：
 * <ul>
 *   <li>{@link #refresh()}：异步读取审计统计数据 → 调用 DeepSeek 生成建议 → 写入内存缓存</li>
 *   <li>{@link #getRecommendations()}：返回缓存中的建议（供 {@code /api/ai/recommendations} 接口使用）</li>
 * </ul>
 * <p>
 * 建议缓存在内存中，由定时任务（{@code RecommendationTask}）每天 0 点触发刷新。
 * AI 生成失败时保留上一次建议，缓存为空时降级到默认建议。
 */
@Service
@Slf4j
public class RecommendationService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Resource
    private DeepSeekService deepSeekService;

    /**
     * 默认建议（AI 不可用时的降级方案）
     */
    private static final List<Map<String, String>> DEFAULT_RECOMMENDATIONS = List.of(
            Map.of("level", "HIGH", "content", "建议将 GUEST 角色用户限制 rm / chmod / iptables 等高危命令的使用权限"),
            Map.of("level", "HIGH", "content", "建议对所有 sudo 操作开启二次确认机制（MFA或审批流程）"),
            Map.of("level", "MEDIUM", "content", "建议限制非管理员用户执行 iptables 和 docker 相关操作"),
            Map.of("level", "MEDIUM", "content", "建议对 wget/curl 下载并通过管道执行的脚本增加事前安全扫描"),
            Map.of("level", "LOW", "content", "建议定期审计 /etc/passwd、/etc/shadow 等敏感文件的访问记录"),
            Map.of("level", "LOW", "content", "建议每周生成审计报告并邮件通知安全管理员")
    );

    /**
     * 建议缓存（volatile 保证多线程可见性，赋值即替换整体列表）
     */
    private static volatile List<Map<String, String>> RECOMMENDATIONS = DEFAULT_RECOMMENDATIONS;

    /**
     * 异步刷新建议（在 recommendExecutor 线程池中执行）
     * <p>
     * 读取本月审计统计数据（状态分布 + 危险命令排行榜），调用 DeepSeek 生成建议，
     * 成功后整体替换缓存；失败则保留上一次建议，不影响前端展示。
     */
    @Async("recommendExecutor")
    public void refresh() {
        log.info("=====开始刷新 AI 策略建议=====");

        String statistics = buildStatistics();

        List<Map<String, String>> generated = deepSeekService.generateRecommendations(statistics);
        if (generated != null && !generated.isEmpty()) {
            RECOMMENDATIONS = generated;
            log.info("AI 策略建议刷新成功，共 {} 条", generated.size());
        } else {
            log.warn("AI 策略建议生成失败或为空，保留上一次建议");
        }
    }

    /**
     * 获取当前缓存的建议（供控制器调用）
     */
    public List<Map<String, String>> getRecommendations() {
        List<Map<String, String>> current = RECOMMENDATIONS;
        return current != null ? current : DEFAULT_RECOMMENDATIONS;
    }

    /**
     * 组装审计统计数据：本月状态分布 + 实时危险命令池 + 危险命令排行榜
     */
    private String buildStatistics() {
        String start = LocalDate.now().withDayOfMonth(1).toString();

        List<Map<String, Object>> statusList = auditLogMapper.countByStatus(start);
        List<Map<String, Object>> ranking = auditLogMapper.getDangerRanking();

        StringBuilder sb = new StringBuilder();
        sb.append("本月（自 ").append(start).append(" 起）审计命令状态分布：\n");
        if (statusList != null && !statusList.isEmpty()) {
            for (Map<String, Object> row : statusList) {
                sb.append("  ").append(statusName(row.get("status")))
                  .append(": ").append(row.get("cnt")).append(" 条\n");
            }
        } else {
            sb.append("  （无数据）\n");
        }

        sb.append("实时危险命令池（用户ID -> 最近危险命令）：\n");
        if (DANGERCMD_MAP != null && !DANGERCMD_MAP.isEmpty()) {
            for (Map.Entry<Long, String> entry : DANGERCMD_MAP.entrySet()) {
                sb.append("  用户ID ").append(entry.getKey())
                  .append(": ").append(entry.getValue()).append("\n");
            }
        } else {
            sb.append("  （无数据）\n");
        }

        sb.append("危险命令用户排行榜（TOP 10）：\n");
        if (ranking != null && !ranking.isEmpty()) {
            for (Map<String, Object> row : ranking) {
                sb.append("  ").append(row.get("username"))
                  .append(": ").append(row.get("danger_count")).append(" 条\n");
            }
        } else {
            sb.append("  （无数据）\n");
        }

        return sb.toString();
    }

    /**
     * 状态码转可读名称：0 正常 / 1 静态拦截 / 2 AI高危 / 3 AI中危
     */
    private static String statusName(Object status) {
        if (status == null) {
            return "未知";
        }
        switch (String.valueOf(status)) {
            case "1":
                return "静态拦截";
            case "2":
                return "AI高危";
            case "3":
                return "AI中危";
            default:
                return "正常";
        }
    }
}
