package com.zhanghan.sshproxyproject.schedule;


import com.zhanghan.sshproxyproject.entity.SessionInfo;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import com.zhanghan.sshproxyproject.mapper.BackendServerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
    private BackendServerMapper backendServerMapper;

    //每分钟执行
    @Scheduled(fixedDelay = 60000)
    public void onlineUserTask(){
//        log.info("========执行心跳检测========");
        //所有会话信息
        for(Map.Entry<String,SessionInfo> entry:
                ONLINE_SESSIONS.entrySet()){
            String sessionId = entry.getKey();
            SessionInfo sessionInfo = entry.getValue();

            //判断活跃时间是否超过20分钟
            if(Duration.between(sessionInfo.getLastActiveTime(), LocalDateTime.now()).toMinutes()>20){
                //超过，则移除（同时扣减服务器连接数），仅当 session 确实还存在时才扣减，防止与 closeAll 竞态
                SessionInfo removed = ONLINE_SESSIONS.remove(sessionId);
                if (removed != null) {
                    backendServerMapper.cutServerNum(sessionInfo.getServerId());
                    log.info("超时会话{}-移除，服务器{}连接数-1", sessionId, sessionInfo.getServerId());
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
