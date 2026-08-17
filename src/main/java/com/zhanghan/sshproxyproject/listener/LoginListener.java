package com.zhanghan.sshproxyproject.listener;

import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.mapper.BackendServerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

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
