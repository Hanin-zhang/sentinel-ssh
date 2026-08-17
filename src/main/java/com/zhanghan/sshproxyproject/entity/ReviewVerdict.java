package com.zhanghan.sshproxyproject.entity;

/**
 * 静态规则审查结论枚举
 * <p>
 * 用于 Phase 1（静态规则引擎）对命令的三分类判定：
 * <ul>
 *   <li><b>BLOCK</b> — 明确危险，直接拦截，不转发到后端</li>
 *   <li><b>ALLOW</b> — 明确安全，直接放行，不需要 AI 审查</li>
 *   <li><b>SUSPICIOUS</b> — 灰区命令，先放行执行，异步提交 AI 二次审查</li>
 * </ul>
 */
public enum ReviewVerdict {

    /** 明确危险 — 直接拦截 */
    BLOCK,

    /** 明确安全 — 直接放行 */
    ALLOW,

    /** 灰区/可疑 — 先放行，异步 AI 审查 */
    SUSPICIOUS
}
