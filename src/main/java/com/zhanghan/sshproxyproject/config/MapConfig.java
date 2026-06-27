package com.zhanghan.sshproxyproject.config;

import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.entity.TerminalSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MapConfig {

    /*
    * 管理会话信息
    * */
    @Bean
    public ConcurrentHashMap<String, TerminalSession> ONLINE_SESSION(){
        return new ConcurrentHashMap<>();
    }

    @Bean
    public ConcurrentHashMap<String, LoginFormDTO> LOGIN_MESSAGE(){
        return new ConcurrentHashMap<>();
    }
}
