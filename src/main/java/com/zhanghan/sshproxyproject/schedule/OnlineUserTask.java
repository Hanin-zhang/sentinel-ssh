package com.zhanghan.sshproxyproject.schedule;


import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import com.zhanghan.sshproxyproject.session.SessionManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static com.zhanghan.sshproxyproject.session.SessionManager.ONLINE_SESSIONS;

@Component
@Slf4j
public class OnlineUserTask {

    @Resource
    private SessionManager sessionManager;

    //每分钟执行一次扫描
    @Scheduled(fixedDelay = 60000)
    public void onlineUserTask(){
//        log.info("========执行心跳检测========");
        //所有会话信息
        for(Map.Entry<String,SessionInfo> entry:
                ONLINE_SESSIONS.entrySet()){
            String sessionId = entry.getKey();
            SessionInfo sessionInfo = entry.getValue();

            // 情况1:底层用户会话已真正结束(异常断开/被踢)→ 及时回收,不必干等20分钟
            ServerSession userSession = sessionInfo.getUserSession();
            boolean sessionDead = userSession == null || !userSession.isOpen();

            // 情况2:会话还活着但超过20分钟无操作 → 空闲超时兜底(防 MINA 未及时发现的半开连接)
            boolean idleTimeout = Duration.between(sessionInfo.getLastActiveTime(), LocalDateTime.now()).toMinutes() > 20;

            if (sessionDead || idleTimeout) {
                try {
                    // 真实回收:closeSession 关闭 用户->代理 与 代理->后端 两条真实连接,
                    // 解除阻塞的转发线程,再统一移出在线池 + 扣减 DB 连接数(remove 非 null 才扣,天然防重)
                    sessionManager.closeSession(sessionId, sessionInfo);
                    log.info("回收会话 -> sessionId={}, serverId={}, 原因={}", sessionId,
                            sessionInfo.getServerId(), sessionDead ? "底层会话已断开" : "空闲超时");
                } catch (Exception e) {
                    // 单个会话回收失败不影响本轮其它会话
                    log.error("回收会话失败 sessionId={}", sessionId, e);
                }
            }
        }
    }

    //每过一天更新一次
    @Scheduled(cron = "0 0 0 * * *")
    public void updateLoginCount(){
        log.info("========更新每日登录次数========");
        //更新每日登录次数
        LoginListener.resetLoginCount();
    }
}
