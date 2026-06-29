package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.ConnectWay;
import com.zhanghan.sshproxyproject.service.TerminalServerce;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

///*
//* 终端相关
//* */
//@RestController
//@Slf4j
//@RequestMapping("/terminal")
//public class TerminalController {
//
//    @Resource
//    private TerminalServerce terminalServerce;
//
//    //选择时指定上传服务器id，否则默认采用轮询
//    @PostMapping("/connect")
//    public Result connectToServer(@RequestParam(required = false) Integer serverId,
//                                  HttpServletRequest request){
//        log.info("用户请求连接服务器-{}",serverId);
//        return terminalServerce.connectToServer(serverId,request);
//    }
//}
