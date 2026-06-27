package com.zhanghan.sshproxyproject;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Scanner;

@SpringBootTest
@Slf4j
class SshProxyProjectApplicationTests {

    @Resource
    private SshClient client;
    @Resource(name = "sshServer2")
    private SshServer server;

    @Test
    void contextLoads() {
    }

    @Test
    void testConcat() {
        log.info("测试客户端连接主机");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Scanner sc = new Scanner(System.in);
//        System.out.println("请输入名称:");
        String username = "admin";
//        System.out.println("请输入密码:");
        String password = "123456";
        try(ClientSession session = client.connect(username,"127.0.0.1",52020)
                .verify(5000)
                .getSession()){
            session.addPasswordIdentity(password);
            session.auth().verify(5000);

        } catch (IOException e) {
            log.error("连接失败");
            throw new RuntimeException(e);
        }
    }
}
