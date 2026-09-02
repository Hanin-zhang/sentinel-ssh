package com.zhanghan.sshproxyproject.listener;

import com.zhanghan.sshproxyproject.core.proxy.ListenManager;
import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.mapper.BackendServerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static com.zhanghan.sshproxyproject.session.SessionManager.ONLINE_SESSIONS;

/*
* 登录监听器，有新会话自动运行方法
* */
@Component
@Slf4j
public class LoginListener implements SessionListener {

    @Resource
    private BackendServerMapper backendServerMapper;

    //统计今日登录次数
    private static final AtomicInteger loginCount = new AtomicInteger(0);

    public static int getLoginCount() {
        return loginCount.get();
    }

    public static void resetLoginCount() {
        loginCount.set(0);
    }

    @Override
    public void sessionCreated(Session session) {
        ServerSession userSession = (ServerSession) session;
        log.info("用户会话创建 -> IP:{}", userSession.getRemoteAddress());
    }

    @Override
    public void sessionEstablished(Session session) {
        ServerSession userSession = (ServerSession) session;
        log.info("用户认证成功 -> 用户:{}", userSession.getRemoteAddress());
    }

//    /**
//     * 会话结束回调 —— 统一收口点。
//     * 用户到代理的 SSH 会话无论以何种方式结束(正常 exit、异常断开、网络中断、强制踢人),
//     * MINA 都会回调此方法;在这里把「在线池 / DB 计数 / 真实到后端的连接」一起回收,
//     * 避免异常断开后只能干等 20 分钟心跳、而心跳又只记账不关真实连接的问题。
//     */
//    @Override
//    public void sessionClosed(Session session, Throwable reason) {
//        // 本监听器挂在代理服务端,只会收到 ServerSession;此处双保险
//        if (!(session instanceof ServerSession serverSession)) {
//            return;
//        }
//        String sessionId = Base64.getEncoder().encodeToString(serverSession.getSessionId());
//        SessionInfo sessionInfo = ONLINE_SESSIONS.get(sessionId);
//        if (sessionInfo == null) {
//            // 未入池(如认证失败)或已被 exit / 心跳 / 强制踢人清理过,无需重复处理
//            return;
//        }
//        log.info("用户会话结束 -> sessionId={}, reason={}, serverId={}",
//                sessionId, reason == null ? "正常关闭" : reason.getMessage(),
//                sessionInfo.getServerId());
//
//        // 1) 关闭 代理->后端 的真实连接:
//        //    异常断开后它还一直占着,这里关闭的同时也释放了 forwardBackendToUser
//        //    那条一直阻塞在读后端输出上的转发线程
//        ClientSession toBackend = sessionInfo.getToBackendSession();
//        if (toBackend != null && toBackend.isOpen()) {
//            try {
//                toBackend.close();
//            } catch (IOException e) {
//                log.warn("关闭后端会话失败 sessionId={}", sessionId, e);
//            }
//        }
//
//        // 2) 从在线池移除并扣减 DB 连接数。
//        //    统一走 removeFromOnlineSessionPool:只有 remove 成功(拿到非 null)才 cutServerNum,
//        //    与 exit / 心跳 / 强制踢人的清理天然互斥,保证同一会话只扣一次
//        removeFromOnlineSessionPool(sessionId, sessionInfo.getServerId());
//
//        // 3) 顺手清理该会话残留的命令缓冲(userStringBuilders 目前只放不删)
//        ListenManager.userStringBuilders.remove(sessionId);
//    }

    //加入在线会话池
    public void addToOnlineSessionPool(SessionInfo sessionInfo, Integer serverId){
        //今日登录次数+1
        loginCount.incrementAndGet();
        //key:sessionId，value:SessionInfo
        ONLINE_SESSIONS.put(sessionInfo.getSessionId(), sessionInfo);
        //修改服务器在线人数
        backendServerMapper.updateServerNum(serverId);
    }

    //从在线会话池移除，只在 session 确实存在于池中时才扣减连接数
    public void removeFromOnlineSessionPool(String sessionId, Integer serverId){
        SessionInfo removed = ONLINE_SESSIONS.remove(sessionId);
        if (removed != null) {
            //修改服务器在线人数
            backendServerMapper.cutServerNum(serverId);
        }
    }
}
