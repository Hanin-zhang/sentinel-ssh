package com.zhanghan.sshproxyproject.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.channel.ServerChannel;
import org.apache.sshd.server.session.ServerSession;

import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class SessionContext {

    //会话中的用户
    private User user;

    //后台服务器
    private BackendServer backendServer;

    //用户->代理的会话
    private ServerChannel userChannel;

    //代理->后台的会话
    private ClientChannel backendChannel;

    //用户输入流
    private InputStream userIn;

    //用户输出流
    private OutputStream userOut;

    //后台输出流
    private InputStream backendOut;

    //后台输入流
    private OutputStream backendIn;

    //各个用户各自的命令输入器
    private StringBuilder commandBuffer;
}
