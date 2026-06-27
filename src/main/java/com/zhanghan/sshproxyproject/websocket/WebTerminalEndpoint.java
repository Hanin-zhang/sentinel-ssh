package com.zhanghan.sshproxyproject.websocket;

import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.entity.TerminalSession;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.listener.AuditLogListener;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import com.zhanghan.sshproxyproject.service.IBackendServerService;
import com.zhanghan.sshproxyproject.service.IUserService;
import jakarta.annotation.Resource;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.tomcat.websocket.WsSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.websocket.Session;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static com.zhanghan.sshproxyproject.common.Constants.BLOCK_COMMAND;
import static com.zhanghan.sshproxyproject.common.utils.PermissionUtil.checkUserPermission;

@ServerEndpoint("/test")
@Slf4j
public class WebTerminalEndpoint {

    @OnOpen
    public void onOpen(Session session){
        log.info("连接成功");
    }

    @OnMessage
    public void onMessage(String msg,Session session)
            throws IOException {

        log.info("收到消息 {}",msg);

        session.getBasicRemote()
                .sendText("echo:"+msg);
    }
}