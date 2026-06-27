package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

@RestController
@Slf4j
@RequestMapping("/ai")
public class AIRiskController {

    @Resource
    private AuditLogMapper auditLogMapper;

    /**
     * 命令风险分析
     */
    @PostMapping("/analyze")
    public Result analyzeCommand(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        if (command == null || command.trim().isEmpty()) {
            return Result.fail("请输入待分析的命令");
        }

        command = command.trim();
        String lower = command.toLowerCase();
        int score;
        String riskLevel;
        String reason;

        // 高危检测
        if (Pattern.compile("rm\\s+-rf|rm\\s+-r\\s+/").matcher(lower).find()
                || lower.contains("dd if=") || lower.contains("mkfs.")
                || lower.matches(".*:\\(\\)\\s*\\{.*") || lower.contains("> /dev/sda")) {
            score = 95;
            riskLevel = "HIGH";
            reason = "检测到高危删除/格式化/覆盖操作，可能导致数据不可逆丢失或系统损坏";
        } else if (lower.contains("chmod 777") || lower.contains("iptables -f")
                || lower.contains("kill -9") || lower.contains("docker rm -f")) {
            score = 78;
            riskLevel = "HIGH";
            reason = "检测到权限全开或破坏性操作，存在严重安全风险";
        } else if (lower.contains("sudo ") || lower.contains("passwd ")
                || lower.contains("useradd ") || lower.contains("usermod ")
                || lower.contains("chown ")) {
            score = 55;
            riskLevel = "MEDIUM";
            reason = "检测到权限/用户管理相关操作，建议确认操作意图和授权";
        } else if (lower.matches(".*wget.*\\|.*sh.*") || lower.matches(".*curl.*\\|.*sh.*")
                || lower.matches(".*\\./.*\\.sh.*")) {
            score = 62;
            riskLevel = "MEDIUM";
            reason = "检测到外部脚本执行模式，存在供应链攻击/恶意脚本风险";
        } else if (lower.contains("docker exec") || lower.contains("kubectl exec")
                || lower.contains("ssh -")) {
            score = 45;
            riskLevel = "MEDIUM";
            reason = "检测到容器/远程主机访问操作，需验证目标合法性和操作权限";
        } else if (lower.contains("iptables ") || lower.contains("docker rm")) {
            score = 40;
            riskLevel = "MEDIUM";
            reason = "检测到网络/容器管理操作，可能影响服务可用性";
        } else {
            score = 15;
            riskLevel = "LOW";
            reason = "未检测到明显安全风险，该命令属于常规运维操作";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("command", command);
        data.put("score", score);
        data.put("riskLevel", riskLevel);
        data.put("reason", reason);

        log.info("AI分析命令: {} -> 风险等级: {}, 评分: {}", command, riskLevel, score);
        return Result.ok(data);
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
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : ranking) {
            Map<String, Object> item = new HashMap<>();
            item.put("username", row.get("username"));
            Object countObj = row.get("danger_count");
            item.put("dangerCount", countObj != null ? Long.parseLong(countObj.toString()) : 0);
            result.add(item);
        }

        return Result.ok(result);
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
}
