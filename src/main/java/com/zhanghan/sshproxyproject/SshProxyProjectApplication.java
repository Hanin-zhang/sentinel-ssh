package com.zhanghan.sshproxyproject;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.zhanghan.sshproxyproject.mapper")
@EnableScheduling   //开启定时任务
@EnableAsync        //开启异步方法支持（AI审查+告警）
public class SshProxyProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(SshProxyProjectApplication.class, args);
    }

}
