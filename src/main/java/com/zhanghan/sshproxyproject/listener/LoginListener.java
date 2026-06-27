package com.zhanghan.sshproxyproject.listener;

import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.session.SessionManager;
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
    private SessionManager sessionManager;

    private final AtomicInteger loginCount = new AtomicInteger(0);

    //统计登录次数
    public static Integer LOGIN_COUNT = 0;

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
    public void addToOnlineSessionPool(SessionInfo sessionInfo){
        //今日登录次数+1
        LOGIN_COUNT = loginCount.incrementAndGet();
        //key:sessionId，value:SessionInfo
        ONLINE_SESSIONS.put(sessionInfo.getSessionId(),sessionInfo);
    }

    public void removeFromOnlineSessionPool(String sessionId){
        ONLINE_SESSIONS.remove(sessionId);
    }
}
