package com.zhanghan.sshproxyproject.schedule;


import ch.qos.logback.core.util.TimeUtil;
import com.zhanghan.sshproxyproject.entity.SessionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

import static com.zhanghan.sshproxyproject.listener.LoginListener.LOGIN_COUNT;
import static com.zhanghan.sshproxyproject.session.SessionManager.ONLINE_SESSIONS;

@Component
@Slf4j
public class OnlineUserTask {

//    //每分钟执行
//    @Scheduled(fixedDelay = 60000)
//    public void OnlineUserTask(){
//        log.info("========执行心跳检测========");
//        //所有会话信息
//        for(Map.Entry<String,SessionInfo> entry:
//                ONLINE_SESSIONS.entrySet()){
//            String sessionId = entry.getKey();
//            SessionInfo sessionInfo = entry.getValue();
//
//            //判断活跃时间是否超过20分钟
//            if(Duration.between(sessionInfo.getLastActiveTime(), LocalDateTime.now()).toMinutes()>20){
//                //超过，则移除
//                ONLINE_SESSIONS.remove(sessionId);
//                log.info("超时会话{}-移除",sessionId);
//            }
//        }
//    }
//
//    //每过一天更新一次
//    @Scheduled(cron = "0 0 0 * * *")
//    public void uodateLoginCount(){
//        log.info("========更新每日登录次数========");
//        //更新每日登录次数
//        LOGIN_COUNT = 0;
//    }
}
