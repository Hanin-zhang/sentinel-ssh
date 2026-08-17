package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 静态规则引擎审查结果
 * <p>
 * 包含审查结论、命中的规则名称、是否需要进行 AI 二次审查等信息。
 * 由 {@code StaticRuleEngine} 生成，供 {@code CommandReviewService} 消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticReviewResult {

    /** 静态审查结论：BLOCK / ALLOW / SUSPICIOUS */
    private ReviewVerdict verdict;

    /** 命中的规则名称（用于审计日志和前端展示） */
    private String matchedRule;

    /** 拦截/可疑原因的简要说明（中文） */
    private String reason;

    /** 是否需要提交 AI 二次审查 */
    private boolean needsAiReview;
}
