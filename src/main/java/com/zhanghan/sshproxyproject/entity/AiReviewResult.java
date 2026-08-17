package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 审查单条命令后的结构化结果
 * 由 DeepSeek 返回的 JSON 反序列化而来
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReviewResult {

    /** 是否有危险 */
    private boolean dangerous;

    /** 风险等级: HIGH / MEDIUM / LOW / SAFE */
    private String level;

    /** 风险原因简述 */
    private String reason;

    /** 风险分类: 数据破坏 / 权限提升 / 信息泄露 / 挖矿 / 后门 / 下载执行 / 正常 */
    private String category;

    // ---- 工厂方法 ----

    /** AI 服务不可用时的降级结果：放行 */
    public static AiReviewResult fallback() {
        return AiReviewResult.builder()
                .dangerous(false)
                .level("SAFE")
                .reason("AI 服务暂时不可用，降级放行")
                .category("正常")
                .build();
    }

    /** 超时时的降级结果：放行 */
    public static AiReviewResult timeout(String command) {
        return AiReviewResult.builder()
                .dangerous(false)
                .level("SAFE")
                .reason("AI 审查超时(" + command + ")，降级放行")
                .category("正常")
                .build();
    }

    /** API 返回非 JSON 时的兜底结果（基于关键词做最基础判断） */
    public static AiReviewResult rawFallback(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return fallback();
        }
        String lower = rawText.toLowerCase();
        boolean looksDangerous = lower.contains("dangerous")
                || lower.contains("高危")
                || lower.contains("malicious");

        return AiReviewResult.builder()
                .dangerous(looksDangerous)
                .level(looksDangerous ? "MEDIUM" : "SAFE")
                .reason("AI 返回非结构化内容，已做关键词兜底: " + rawText)
                .category("未知")
                .build();
    }
}
