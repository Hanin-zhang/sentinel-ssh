package com.zhanghan.sshproxyproject.config;

import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
@Slf4j
public class SshClientConfig {

    //创建客户端模板
    @Bean(destroyMethod = "stop")
    public SshClient sshClient(){
        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        log.info("全局SSH代理客户端启动");
        return client;
    }
//    // 项目关闭时自动stop()
//    @PreDestroy
//    public void stopClient() {
//        if()
//        sshClient().stop();
//        log.info("全局SSH客户端已关闭");
//    }
}
