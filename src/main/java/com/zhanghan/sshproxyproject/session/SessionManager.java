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
    * 强制断连，用于用户输入危险指令
    * */
    public void forceDisconnect(String sessionId){

        SessionInfo sessionInfo = ONLINE_SESSIONS.get(sessionId);

        closeSession(sessionId, sessionInfo);
    }

    private void closeSession(String sessionId, SessionInfo sessionInfo) {
        ServerSession userSession = sessionInfo.getUserSession();
        ClientSession toBackendSession = sessionInfo.getToBackendSession();
        Integer serverId = sessionInfo.getServerId();
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

}
