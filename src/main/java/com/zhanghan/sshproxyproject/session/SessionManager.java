package com.zhanghan.sshproxyproject.session;

import com.zhanghan.sshproxyproject.entity.DangerCmdInfo;
import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.entity.TerminalSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionManager {
    //在线会话池
    public static final Map<String, SessionInfo> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    //统计危险命令  存放用户id以及危险命令对应的信息
    public static final Map<Long, String> DANGERCMD_MAP = new ConcurrentHashMap<>();

    //TODO 实现接口
    //查询在线用户
    public static Integer getOnlineNum(){
        return ONLINE_SESSIONS.size();
    }

    public static Integer getDangerCmdNum(){
        return DANGERCMD_MAP.size();
    }

}
