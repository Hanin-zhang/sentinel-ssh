package com.zhanghan.sshproxyproject.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

//@Configuration
//@Slf4j
//// 非Web环境（单元测试）不加载该配置
//@ConditionalOnWebApplication
//public class WebSocketConfig {
//
//    @Bean
//    public ServerEndpointExporter serverEndpointExporter(){
//        log.info("======ServerEndpointExporter创建成功======");
//        return new ServerEndpointExporter();
//    }
//}
