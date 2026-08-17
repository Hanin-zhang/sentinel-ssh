package com.zhanghan.sshproxyproject.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnlineUserVO {

    //会话ID
    private String sessionId;

    //用户名
    private String username;

    //角色
    private String role;

    //客户端IP
    private String clientIp;

    //连接的服务器ID
    private Integer serverId;

    //服务器名称
    private String serverName;

    //服务器IP
    private String serverHost;

    //登录时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime loginTime;

    //最后活跃时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastActiveTime;

    //在线时长（分钟）
    private Long durationMinutes;
}
