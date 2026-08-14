package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令处理结果类
 * <p>
 * 封装静态审查 + 动态 AI 审查的综合判定结果，
 * 由 {@code CommandReviewService} 统一返回，
 * 供 {@code ProxyForwarder} 决定：放行 / 拦截 / 异步告警。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommandProcessor {

    // ==================== 基础判定字段 ====================

    /** 是否因危险而被拦截（静态规则 + AI HIGH 的综合结论） */
    private boolean blocked = false;

    /** 角色权限是否通过（admin 全部通过，operator/guest 受白名单限制） */
    private boolean permitted = false;

    /** 是否因连接异常而断开 */
    private boolean disconnect;

    /** 返回给用户终端的提示消息（如权限不足、命令被拦截等） */
    private String message;

    // ==================== 静态审查字段（新增） ====================

    /** 静态规则引擎的审查结论：BLOCK / ALLOW / SUSPICIOUS */
    private ReviewVerdict staticVerdict;

    /** 命中的静态规则名称（如 "rm -rf / 高危删除"、"反弹Shell签名匹配"） */
    private String matchedRule;

    /** 是否需要提交 AI 二次审查（仅 SUSPICIOUS 时为 true） */
    private boolean needsAiReview;

    // ==================== AI 审查字段（新增，异步回填） ====================

    /** AI 审查的结构化结果（异步审查完成后回填，初始为 null） */
    private AiReviewResult aiResult;

    // ==================== 工厂方法 ====================

    /**
     * 快速构造一个"直接放行"的结果
     *
     * @param message 放行原因简述
     */
    public static CommandProcessor allow(String message) {
        return CommandProcessor.builder()
                .blocked(false)
                .permitted(true)
                .disconnect(false)
                .message(message)
                .staticVerdict(ReviewVerdict.ALLOW)
                .needsAiReview(false)
                .build();
    }

    /**
     * 快速构造一个"直接拦截"的结果
     *
     * @param message     拦截原因简述
     * @param matchedRule 命中的规则名
     */
    public static CommandProcessor block(String message, String matchedRule) {
        return CommandProcessor.builder()
                .blocked(true)
                .permitted(false)
                .disconnect(false)
                .message(message)
                .staticVerdict(ReviewVerdict.BLOCK)
                .matchedRule(matchedRule)
                .needsAiReview(false)
                .build();
    }

    /**
     * 快速构造一个"灰区/可疑，需要 AI 审查"的结果
     *
     * @param message     可疑原因简述
     * @param matchedRule 命中的可疑规则名
     */
    public static CommandProcessor suspicious(String message, String matchedRule) {
        return CommandProcessor.builder()
                .blocked(false)          // 灰区命令先放行
                .permitted(true)
                .disconnect(false)
                .message(message)
                .staticVerdict(ReviewVerdict.SUSPICIOUS)
                .matchedRule(matchedRule)
                .needsAiReview(true)     // 标记需要 AI 异步审查
                .build();
    }
}
