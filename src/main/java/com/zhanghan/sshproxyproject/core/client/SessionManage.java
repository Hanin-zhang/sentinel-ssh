package com.zhanghan.sshproxyproject.core.client;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/*
1、创建会话
2、监听线程池负责将会话请求发往后端
3、工作线程池负责校验信息，成功则建立双向连接
* */
@Slf4j
@Component
public class SessionManage {

    @Resource
    private SshClient client;
    @Resource(name = "workExecutor")
    private Executor workThreadPool;

    //申请创建会话
    public void createSession(ServerSession userSession){
        log.info("接收用户会话，开始分配后端连接 → 用户:{},ip:{}",userSession.getUsername(),userSession.getRemoteAddress());

        //将任务异步交付给工作线程池
        workThreadPool.execute(()->{
            doAndCheckSession(userSession,"admin","123456");
        });
    }

    //实现认证校验
    //TODO 实现传参(username,password)
    public void doAndCheckSession(ServerSession userSession,String username,String password){
        log.info("进行认证校验->用户:{},ip:{}",userSession.getUsername(),userSession.getRemoteAddress());
        try {
            //建立连接
            ClientSession session = client.connect(username,"127.0.0.1",52022)
                    .verify(5000)
                    .getSession();
            //密码校验
            session.addPasswordIdentity(password);
            session.auth().verify(5000);

            log.info("{}->连接成功",userSession.getUsername());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
