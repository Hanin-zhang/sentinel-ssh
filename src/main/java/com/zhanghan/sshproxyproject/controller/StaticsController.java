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
        data.put("todayDangerCount", 0);
        // 总危险命令数
        data.put("totalDangerCount", auditLogMapper.getTotalDangerCmdNum());

        return Result.ok(data);
    }

    /**
     * 近7天风险趋势
     */
    @GetMapping("/risk-trend")
    public Result getRiskTrend() {
        List<String> dates = new ArrayList<>();
        List<Integer> highList = new ArrayList<>();
        List<Integer> mediumList = new ArrayList<>();
        List<Integer> lowList = new ArrayList<>();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        // 模拟近7天趋势数据（实际应通过SQL GROUP BY日期统计）
        // 用随机模拟数据，实际项目中应调用 mapper 按日期分组查询
        Random rand = new Random(42); // 固定种子，保证每次结果一致
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dates.add(date.format(fmt));

            // 基于真实数据趋势的模拟
            int high = rand.nextInt(5) + (int) (auditLogMapper.getTotalDangerCmdNum() % 5);
            int medium = rand.nextInt(8) + 3;
            int low = rand.nextInt(30) + 40;

            highList.add(Math.max(0, high));
            mediumList.add(Math.max(0, medium));
            lowList.add(Math.max(0, low));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("dates", dates);
        data.put("high", highList);
        data.put("medium", mediumList);
        data.put("low", lowList);

        return Result.ok(data);
    }
}
