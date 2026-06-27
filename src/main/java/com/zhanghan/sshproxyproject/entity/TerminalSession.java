package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;

import java.io.InputStream;
import java.io.OutputStream;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TerminalSession {

    private String sessionId;

    private Long userId;

    private String userIp;

    private Integer serverId;

    //给客户端的
    private ClientSession clientSession;

    private ClientChannel clientChannel;

    private InputStream userOut;

    private OutputStream userIn;

    // 当前命令缓冲区
    private StringBuilder commandBuffer;

}
