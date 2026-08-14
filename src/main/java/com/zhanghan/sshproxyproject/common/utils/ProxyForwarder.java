package com.zhanghan.sshproxyproject.common.utils;


import com.zhanghan.sshproxyproject.core.proxy.ListenManager;
import com.zhanghan.sshproxyproject.core.server.BackendManager;
import com.zhanghan.sshproxyproject.entity.*;
import com.zhanghan.sshproxyproject.listener.AuditLogListener;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import com.zhanghan.sshproxyproject.service.CommandReviewService;
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
    @Resource
    private CommandReviewService commandReviewService;   // 两级命令审查服务（新增）

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

        Integer serverId = sessionContext.getBackendServer().getId();

        if (backOut == null) {
            log.error("❌ 后端通道异常，backOut为空");
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId,serverId);
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
                forwardBackendToUser(userOut, toBackendChannel,userChannel,sessionId,serverId);
            });

            log.info("✅✅双向转发通道建立成功✅✅");
        } catch(Exception e){
                log.error("建立双向转发通道失败", e);
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId,serverId);
            }
        }


    //关闭会话              用户<->代理                      代理<->服务器
    private void closeAll(ServerSession userSession , ClientSession toBackendSession,String sessionId,Integer serverId){
        try {
            log.info("关闭会话");
            if(userSession != null) {
                userSession.close();
            }
            if(toBackendSession != null) {
                toBackendSession.close();
            }
            //监听器删除相关信息
            loginListener.removeFromOnlineSessionPool(sessionId,serverId);
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

                String command = null;

                //按下回车 → 命令组装完成，触发审查
                if (str.contains("\r") || str.contains("\n")) {
                    //拼接出一整条命令
                    command = stringBuilder.toString().trim();

                    //更新活跃时间
                    sessionInfo.setLastActiveTime(LocalDateTime.now());

                    log.info("用户输入: {}", command);

                    // ============================================================
                    // 两级命令审查：Phase 1 静态规则 + Phase 2 异步 AI（新增改造）
                    // 替代原有的 BLOCK_COMMAND.contains() + checkUserPermission
                    // ============================================================
                    //此处调用异步方法submitAsyncAiReview
                    CommandProcessor reviewResult = commandReviewService.review(command, user);

                    // 1. 根据审查结果确定审计日志状态
                    int status;  // 0=正常 1=拦截
                    if (reviewResult.isBlocked()) {
                        status = 1;

                        // 记录危险指令（统计用）
                        Long userId = user.getId();
                        addIntoDangerCmdMap(userId, command);
                        boolean isSuccess = userMapper.updateDangerCmd(userId);
                        if (!isSuccess) {
                            log.error("更新用户危险指令数目时出错");
                            throw new RuntimeException();
                        }
                    } else {
                        status = 0;
                    }

                    // 2. 记录审计日志（无论拦截与否都记录）
                    auditLogListener.recordAuditLog(command, user, server, status, userIp);

                    // 清空命令缓冲区
                    stringBuilder.setLength(0);

                    // 3. 检测用户是否主动退出
                    if (command.equals("exit")) {
                        log.info("用户{}退出系统，主动关闭", user.getUsername());
                        closeAll(userChannel.getServerSession(), toBackendChannel.getClientSession(),
                                sessionId, sessionContext.getBackendServer().getId());
                        break;
                    }

                    // 4. 根据审查结果分流处理
                    if (reviewResult.isBlocked()) {
                        // ---- BLOCK：拦截，不转发 ----
                        // 原因可能是: 角色权限不足 OR 静态规则命中高危
                        String blockMsg = "\r\n[SYSTEM] " + reviewResult.getMessage() + "\r\n";
                        if (reviewResult.getMatchedRule() != null) {
                            blockMsg = "\r\n[SYSTEM] 命令被拦截: " + reviewResult.getMatchedRule()
                                    + " — " + reviewResult.getMessage() + "\r\n";
                        }
                        log.warn("命令被拦截: sessionId={}, cmd='{}', rule='{}', reason='{}'",
                                sessionId, abbreviate(command),
                                reviewResult.getMatchedRule(), reviewResult.getMessage());

                        userOut.write(blockMsg.getBytes(StandardCharsets.UTF_8));
                        userOut.flush();
                        // 发送 Ctrl+C 中断信号，防止命令残留
                        sendInterruptSignal(backendIn);
                        continue;
                    }

                    if (reviewResult.isNeedsAiReview()) {
                        // ---- SUSPICIOUS：灰区命令，先放行 + 异步 AI 审查 ----
                        // 命令已正常转发到后端执行，同时后台触发 AI 二次确认。
                        // 如果 AI 判定 HIGH，AlertService 会异步写入告警日志、
                        // WebSocket 推送管理员，极端情况（反弹 Shell）可强制断连。
                        log.info("灰区命令已放行，异步AI审查已提交: cmd='{}', rule='{}'",
                                abbreviate(command), reviewResult.getMatchedRule());

                        // 异步 AI 审查（带告警回调）
                        commandReviewService.submitAsyncAiReviewWithAlert(
                                command, user, server, sessionId, userIp);
                    }
                    // else: ALLOW → 静默放行，不做额外处理

                }
                // 正常转发数据到后端（BLOCK 情况已被 continue 跳过，不会到达此处）
                backendIn.write(buf, 0, len);
                backendIn.flush();
            }
        } catch (IOException e) {
            log.error("用户->后台异常", e);
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId,sessionContext.getBackendServer().getId());
        }
    }

    //io线程池任务：处理后台->用户传出的数据
    private void forwardBackendToUser(OutputStream userOut, ClientChannel toBackendChannel,ServerChannel userChannel,String sessionId,Integer serverId) {
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
            closeAll(userChannel.getServerSession(),toBackendChannel.getClientSession(),sessionId,serverId);
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

    /** 截断过长命令用于日志输出（避免刷屏） */
    private static String abbreviate(String cmd) {
        if (cmd == null) return "null";
        return cmd.length() > 100 ? cmd.substring(0, 100) + "..." : cmd;
    }
}
