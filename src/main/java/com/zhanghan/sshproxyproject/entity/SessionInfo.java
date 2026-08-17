package com.zhanghan.sshproxyproject.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.web.bind.support.SessionStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionInfo {

    private String sessionId;

    private Long userId;

    private String username;

    private String role;

    private String clientIp;

    private Integer serverId;

    private LocalDateTime loginTime;

    private LocalDateTime lastActiveTime;   //上次活跃时间，用来心跳检测

    private Integer status; //1，在线 0，不在线

    //代理->后台
    private ClientSession toBackendSession;

    //用户->代理
    private ServerSession userSession;
}
