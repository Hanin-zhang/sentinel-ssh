package com.zhanghan.sshproxyproject.listener;

import com.zhanghan.sshproxyproject.entity.AuditLog;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.service.IAuditLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EventListener;

@Component
@Slf4j
public class AuditLogListener implements EventListener {

    @Resource
    private IAuditLogService auditLogService;

    //记录日志
    public void recordAuditLog(String command, User user, BackendServer server,Integer status,String userIp){
        //记录日志，封装log日志对象
        AuditLog myLog = AuditLog.builder()
                .command(command)
                .status(status)
                .clientIp(userIp)
                .username(user.getUsername())
                .userId(user.getId())
                .serverId(server.getId())
                .serverHost(server.getHost())
                .serverPort(server.getPort())
                .createTime(LocalDateTime.now())
                .build();
        //添加日志进数据库
        auditLogService.save(myLog);
        log.info("记录日志成功->用户：{}",user.getUsername());
    }
}
