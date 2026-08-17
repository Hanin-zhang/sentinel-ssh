package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.service.IDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "仪表盘", description = "首页统计 / 服务器列表 / 在线用户")
@RestController
@Slf4j
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    private IDashboardService dashboardService;

    /*
    * 获取  总用户  在线用户  今日命令    危险命令 等信息
    * */
    @Operation(summary = "获取首页数据", description = "总用户数、在线用户、今日命令、危险命令等")
    @GetMapping("/overview")
    public Result getTotalData(){
        log.info("获取首页数据");
        return dashboardService.getData();
    }

    /*
    * 获取服务器列表
    * */
    @Operation(summary = "获取服务器列表")
    @GetMapping("/servers")
    public Result getServers(){
        return dashboardService.getServers();
    }

    /*
    * 获取在线用户列表（含用户名、IP、服务器名等详情）
    * */
    @Operation(summary = "获取在线用户列表", description = "含用户名、IP、服务器名、在线时长等")
    @GetMapping("/online-users")
    public Result getOnlineUsers(){
        return dashboardService.getOnlineUsers();
    }

}
