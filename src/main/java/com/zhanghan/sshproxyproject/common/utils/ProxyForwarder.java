package com.zhanghan.sshproxyproject.common.utils;


import com.zhanghan.sshproxyproject.core.proxy.ListenManager;
import com.zhanghan.sshproxyproject.core.server.BackendManager;
import com.zhanghan.sshproxyproject.entity.*;
import com.zhanghan.sshproxyproject.listener.AuditLogListener;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import com.zhanghan.sshproxyproject.service.IAuditLogService;
import com.zhanghan.sshproxyproject.service.IBackendServerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoReadFuture;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.channel.ChannelDataReceiver;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.ServerChannel;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.zhanghan.sshproxyproject.common.Constants;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import com.zhanghan.sshproxyproject.core.proxy.ListenManager.*;
import com.zhanghan.sshproxyproject.common.utils.PermissionUtil.*;

import static com.zhanghan.sshproxyproject.common.Constants.BLOCK_COMMAND;
import static com.zhanghan.sshproxyproject.common.utils.PermissionUtil.checkUserPermission;
import static com.zhanghan.sshproxyproject.session.SessionManager.DANGERCMD_MAP;
import static com.zhanghan.sshproxyproject.session.SessionManager.ONLINE_SESSIONS;

//实现连通双向转发通道的工具类

/*
✅可以拦截命令
✅ 可以记录审计日志
✅ 可以做黑白名单过滤
✅ 可以统计流量
* */

@Component
@Slf4j
public class ProxyForwarder {

    @Resource(name = "workExecutor")
    private Executor workThreadPool;
    @Resource(name = "ioExecutor")
    private Executor ioThreadPool;
    @Resource
    private AuditLogListener auditLogListener;
    @Autowired
    private BackendManager backendManager;
    @Resource
    private IBackendServerService backendServerService;
    @Resource
    private LoginListener loginListener;
    @Resource
    private UserMapper userMapper;

    //实现连通双向转发通道,用户 <--> 代理 <--> 后端真实服务器
    public void forward(SessionContext sessionContext) {

        //提取出sessionContext里面的变量
        //userIn,userOut,userChannel, toBackendChannel,proxyUser,server
        OutputStream userOut = sessionContext.getUserOut();
        ClientChannel toBackendChannel = sessionContext.getBackendChannel();
        ServerChannel userChannel = sessionContext.getUserChannel();

        //获取sessionId
        String sessionId = Base64.getEncoder().encodeToString(userChannel.getSession().getSessionId());

        InputStream backOut = toBackendChannel.getInvertedOut();

        if (backOut == null) {
            log.error("❌ 后端通道异常，backOut为空");
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId);
            return;
        }

        //建立双向通道
        try {
            //io线程池处理代理->后台的数据，代理->后台
            ioThreadPool.execute(() -> {
                        forwardUserToBackend(sessionContext);
                    }
            );

            //io线程池处理后台->用户传出的数据，后台->代理->用户
            ioThreadPool.execute(() -> {
                forwardBackendToUser(userOut, toBackendChannel,userChannel,sessionId);
            });

            log.info("✅✅双向转发通道建立成功✅✅");
        } catch(Exception e){
                log.error("建立双向转发通道失败", e);
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId);
            }
        }


    //关闭会话              用户<->代理                      代理<->服务器
    private void closeAll(ServerSession userSession , ClientSession toBackendSession,String sessionId){
        try {
            log.info("关闭会话");
            if(userSession != null) {
                userSession.close();
            }
            if(toBackendSession != null) {
                toBackendSession.close();
            }
            loginListener.removeFromOnlineSessionPool(sessionId);
        } catch (IOException e) {
            log.error("关闭会话失败",e);
            throw new RuntimeException();
        }
    }


    //IO线程池任务：将用户输入的信息传入后台，用户->后台
    private void forwardUserToBackend(SessionContext sessionContext) {

        //获取封装类变量
        InputStream userIn = sessionContext.getUserIn();
        OutputStream userOut = sessionContext.getUserOut();
        User user = sessionContext.getUser();
        BackendServer server = sessionContext.getBackendServer();
        ClientChannel toBackendChannel = sessionContext.getBackendChannel();
        ServerChannel userChannel = sessionContext.getUserChannel();

        //获取会话id
        ServerSession serverSession = userChannel.getServerSession();
        String sessionId = Base64.getEncoder().encodeToString(serverSession.getSessionId());
        //获取用户ip
        String userIp = serverSession.getRemoteAddress().toString();
        //获取对应的对话缓冲区
        StringBuilder stringBuilder = ListenManager.userStringBuilders.get(sessionId);
        //后端输入
        OutputStream backendIn = toBackendChannel.getInvertedIn();

        //获取、更新当前sessionInfo的lastActiveTime，用来心跳检测
        SessionInfo sessionInfo = ONLINE_SESSIONS.get(sessionId);

        byte[] buf = new byte[8192];
        int len;
        try {
            while ((len = userIn.read(buf)) != -1) {
                String str = new String(buf, 0, len);
                stringBuilder.append(str);

                boolean blocked = false;
                String command = null;

                //按下回车
                if (str.contains("\r") || str.contains("\n")) {
                    //合成的r一整条命令
                    command = stringBuilder.toString().trim();

                    //更新活跃时间
                    sessionInfo.setLastActiveTime(LocalDateTime.now());

                    log.info("用户输入{}", command);

                    //查询指令是否不合规
                    //TODO 待完善采用特殊手段跳过检测
                    blocked = BLOCK_COMMAND.stream()
                            .anyMatch(command::contains);   //查询是否危险
                    //blocked为true，则说明有危险，标记为1
                    int status = blocked ? 1 : 0;
                    boolean permitted = checkUserPermission(user, command); //判断权限

                    //记录危险指令
                    if(blocked){
                        Long userId = user.getId();
                        //添加map
                        addIntoDangerCmdMap(userId,command);
                        //添加进数据库
                        boolean isSuccess = userMapper.updateDangerCmd(userId);
                        if(!isSuccess){
                            log.error("更新用户危险指令数目时出错");
                            throw new RuntimeException();
                        }
                    }

                    //使用事件监听器记录日志
                    auditLogListener.recordAuditLog(command,user,server,status,userIp);

                    //清空
                    stringBuilder.setLength(0);

                    //检测用户是否退出
                    if (command.equals("exit")) {
                        log.info("用户{}退出系统，主动关闭", user.getUsername());
                        closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId);
                        break;
                    }

                    //如果权限不足，返回失败
                    if (!permitted) {
                        log.info("用户{}角色为{}，权限不足!!", user.getUsername(), user.getRole());
                        String msg = "\r\n[SYSTEM] Permission denied!\r\n";
                        //输入ctrl+c终止当前对话，0x03，强制中断信号
                        sendInterruptSignal(backendIn);
                        continue;
                    }

                    //如果不合规，拦截
                    if (blocked) {
                        log.info("{}，该对话输入的指令{}有危险，已被拦截", sessionId, command);
                        String msg = "\r\n[SYSTEM] Dangerous command blocked!\r\n";
                        userOut.write(msg.getBytes(StandardCharsets.UTF_8));
                        userOut.flush();

                        //输入ctrl+c终止当前对话，0x03，强制中断信号
                        sendInterruptSignal(backendIn);
                        continue;
                    }

                }
                //正常则转发
                backendIn.write(buf, 0, len);
                backendIn.flush();
            }
        } catch (IOException e) {
            log.error("用户->后台异常", e);
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId);
        }
    }

    //io线程池任务：处理后台->用户传出的数据
    private void forwardBackendToUser(OutputStream userOut, ClientChannel toBackendChannel,ServerChannel userChannel,String sessionId) {
        byte[] buf = new byte[8192];
        int len;
        //后端->代理->用户，后端输出返回的内容
        InputStream backOut = toBackendChannel.getInvertedOut();

        try {
            while ((len = backOut.read(buf)) != -1) {
                userOut.write(buf, 0, len);
                userOut.flush();
            }
        } catch (IOException e) {
            log.error("后端->用户异常", e);
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId);
        }
    }

    //发送中断信号，ctrl+c
    private void sendInterruptSignal(OutputStream out) throws IOException {
        out.write(new byte[]{3});
        out.flush();
    }

    private void addIntoDangerCmdMap(Long userId , String cmd){
        //添加进入危险map
        DANGERCMD_MAP.put(userId,cmd);
    }
}
