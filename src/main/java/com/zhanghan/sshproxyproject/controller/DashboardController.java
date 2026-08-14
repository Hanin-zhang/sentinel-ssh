package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.service.IDashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    private IDashboardService dashboardService;

    /*
    * 获取  总用户  在线用户  今日命令    危险命令 等信息
    * */
    @GetMapping("/overview")
    public Result getTotalData(){
        log.info("获取首页数据");
        return dashboardService.getData();
    }

    /*
    * 获取服务器列表
    * */
    @GetMapping("/servers")
    public Result getServers(){
        return dashboardService.getServers();
    }

    /*
    * 获取在线用户列表（含用户名、IP、服务器名等详情）
    * */
    @GetMapping("/online-users")
    public Result getOnlineUsers(){
        return dashboardService.getOnlineUsers();
    }

}
