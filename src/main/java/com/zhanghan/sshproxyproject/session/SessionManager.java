package com.zhanghan.sshproxyproject.session;

import com.zhanghan.sshproxyproject.common.utils.ProxyForwarder;
import com.zhanghan.sshproxyproject.entity.DangerCmdInfo;
import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.entity.TerminalSession;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionManager {

    @Resource
    private LoginListener loginListener;

    //在线会话池
    public static final Map<String, SessionInfo> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    //统计危险命令  存放用户id以及危险命令对应的信息
    public static final Map<Long, String> DANGERCMD_MAP = new ConcurrentHashMap<>();

    //查询在线用户
    public static Integer getOnlineNum(){
        return ONLINE_SESSIONS.size();
    }

    public static Integer getDangerCmdNum(){
        return DANGERCMD_MAP.size();
    }


    /*
    * 强制断连，用于用户输入危险指令或管理员踢人
    * */
    public boolean forceDisconnect(String sessionId){

        SessionInfo sessionInfo = ONLINE_SESSIONS.get(sessionId);
        if (sessionInfo == null) {
            log.warn("强制断连失败，会话不存在或已断开: sessionId={}", sessionId);
            return false;
        }
        log.warn("🚨 强制断开会话: sessionId={}, serverId={}", sessionId, sessionInfo.getServerId());
        closeSession(sessionId, sessionInfo);
        return true;
    }

    public void closeSession(String sessionId, SessionInfo sessionInfo) {
        ServerSession userSession = sessionInfo.getUserSession();
        ClientSession toBackendSession = sessionInfo.getToBackendSession();
        Integer serverId = sessionInfo.getServerId();
        log.info("关闭会话 sessionId={}, serverId={}", sessionId, serverId);

        // 两条真实连接各自容错:一条 close 失败不影响另一条
        if (userSession != null) {
            try {
                userSession.close();
            } catch (IOException e) {
                log.warn("关闭用户会话失败 sessionId={}", sessionId, e);
            }
        }
        if (toBackendSession != null) {
            try {
                toBackendSession.close();
            } catch (IOException e) {
                log.warn("关闭后端会话失败 sessionId={}", sessionId, e);
            }
        }
        // 无论上面是否抛异常,都要把会话移出在线池并扣减 DB 连接数,
        // 避免异常关闭的会话一直卡在池里(remove 非 null 才扣,与 closeAll/心跳竞态安全)
        loginListener.removeFromOnlineSessionPool(sessionId, serverId);
    }

}
