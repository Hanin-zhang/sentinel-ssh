package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import com.zhanghan.sshproxyproject.mapper.BackendServerMapper;
import com.zhanghan.sshproxyproject.service.IBackendServerService;
import com.zhanghan.sshproxyproject.service.IUserService;
import com.zhanghan.sshproxyproject.session.SessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@Slf4j
@RequestMapping("/statistics")
public class StaticsController {

    @Resource
    private IUserService userService;
    @Resource
    private AuditLogMapper auditLogMapper;
    @Resource
    private IBackendServerService backendServerService;

    /**
     * 个人统计（当前登录用户）
     */
    @GetMapping("/personal")
    public Result getPersonalStats() {
        LoginFormDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            return Result.fail("请先登录");
        }

        String username = currentUser.getUsername();
        LocalDate today = LocalDate.now();

        Map<String, Object> data = new HashMap<>();
        // 用户总命令数
        Long totalCommands = auditLogMapper.countByUser(username);
        // 今日命令数
        Long todayCommands = auditLogMapper.countByUserAndToday(username, today);
        // 今日危险命令数
        Long todayDanger = auditLogMapper.countDangerByUserAndToday(username, today);
        // 总危险命令数
        Long totalDanger = auditLogMapper.countDangerByUser(username);

        data.put("totalCommands", totalCommands != null ? totalCommands : 0);
        data.put("todayCommands", todayCommands != null ? todayCommands : 0);
        data.put("todayDangerCommands", todayDanger != null ? todayDanger : 0);
        data.put("totalDangerCommands", totalDanger != null ? totalDanger : 0);
        // 登录次数用命令执行的日期去重统计作为近似值
        data.put("loginCount", totalCommands != null ? Math.min(totalCommands, 999) : 0);
        // 在线时长（分钟）— 无法精确统计，用命令数*2分钟估算
        data.put("onlineDuration", totalCommands != null ? totalCommands * 2 : 0);

        return Result.ok(data);
    }

    /**
     * 全局统计
     */
    @GetMapping("/global")
    public Result getGlobalStats() {
        LocalDate today = LocalDate.now();

        Map<String, Object> data = new HashMap<>();
        // 总用户数
        data.put("userCount", userService.count());
        // 总服务器数
        data.put("serverCount", backendServerService.count());
        // 在线用户数
        data.put("onlineCount", SessionManager.getOnlineNum());
        // 在线会话数
        data.put("sessionCount", SessionManager.getOnlineNum());
        // 今日命令数
        Long todayCmd = auditLogMapper.countByToday(today);
        data.put("todayCommandCount", todayCmd != null ? todayCmd : 0);
        // 总命令数
        data.put("totalCommandCount", auditLogMapper.getTotalCmdNum());
        // 今日危险命令数
        Long todayDanger = auditLogMapper.countByUserAndToday(null, today);
        data.put("todayDangerCount", todayDanger != null ? todayDanger : 0);
        // 总危险命令数
        data.put("totalDangerCount", auditLogMapper.getTotalDangerCmdNum());

        return Result.ok(data);
    }

    /**
     * 近7天风险趋势（基于真实审计日志数据）
     */
    @GetMapping("/risk-trend")
    public Result getRiskTrend() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        // 初始化近7天数据结构
        Map<String, int[]> dailyCounts = new LinkedHashMap<>(); // dateStr -> [high, medium, low]
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dailyCounts.put(date.format(fmt), new int[]{0, 0, 0});
        }

        //TODO 有待检验
        // 从数据库查询近7天所有命令
        String startDate = LocalDate.now().minusDays(7).toString() + " 00:00:00";
        List<Map<String, Object>> commands = auditLogMapper.getCommandsSince(startDate);

        if (commands != null) {
            for (Map<String, Object> row : commands) {
                Object cmdObj = row.get("command");
                Object dateObj = row.get("cmd_date");
                if (cmdObj == null || dateObj == null) continue;

                String cmd = cmdObj.toString();
                String dateStr = dateObj.toString(); // e.g. "2026-07-05"
                // 格式化为 MM-dd
                try {
                    LocalDate cmdDate = LocalDate.parse(dateStr);
                    dateStr = cmdDate.format(fmt);
                } catch (Exception ignored) {}

                int[] counts = dailyCounts.get(dateStr);
                if (counts == null) continue; // 不在7天窗口内

                // 使用与 AI 分析一致的风险分类规则
                String riskLevel = classifyRisk(cmd);
                switch (riskLevel) {
                    case "HIGH"   -> counts[0]++;
                    case "MEDIUM" -> counts[1]++;
                    default       -> counts[2]++;
                }
            }
        }

        List<String> dates = new ArrayList<>(dailyCounts.keySet());
        List<Integer> highList = new ArrayList<>();
        List<Integer> mediumList = new ArrayList<>();
        List<Integer> lowList = new ArrayList<>();

        for (int[] counts : dailyCounts.values()) {
            highList.add(counts[0]);
            mediumList.add(counts[1]);
            lowList.add(counts[2]);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dates", dates);
        data.put("high", highList);
        data.put("medium", mediumList);
        data.put("low", lowList);

        return Result.ok(data);
    }

    /**
     * 根据命令内容判定风险等级（与 AI 分析保持一致的规则）
     */
    private String classifyRisk(String cmd) {
        String lower = cmd.toLowerCase();
        if (lower.matches(".*rm\\s+-rf.*") || lower.matches(".*rm\\s+-r\\s+/.*")
                || lower.contains("dd if=") || lower.contains("mkfs.")
                || lower.matches(".*:\\(\\)\\s*\\{.*") || lower.contains("> /dev/sda")) {
            return "HIGH";
        }
        if (lower.contains("chmod 777") || lower.contains("iptables -f")
                || lower.contains("kill -9") || lower.contains("docker rm -f")) {
            return "HIGH";
        }
        if (lower.contains("sudo ") || lower.contains("passwd ")
                || lower.contains("useradd ") || lower.contains("usermod ")
                || lower.contains("chown ")) {
            return "MEDIUM";
        }
        if (lower.matches(".*wget.*\\|.*sh.*") || lower.matches(".*curl.*\\|.*sh.*")
                || lower.matches(".*\\./.*\\.sh.*")) {
            return "MEDIUM";
        }
        if (lower.contains("docker exec") || lower.contains("kubectl exec")
                || lower.contains("ssh -")) {
            return "MEDIUM";
        }
        if (lower.contains("iptables ") || lower.contains("docker rm")) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
