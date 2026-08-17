package com.zhanghan.sshproxyproject.core.proxy;

import com.zhanghan.sshproxyproject.common.utils.ProxyForwarder;
import com.zhanghan.sshproxyproject.core.server.BackendManager;
import com.zhanghan.sshproxyproject.entity.*;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import com.zhanghan.sshproxyproject.service.ISysRoleService;
import com.zhanghan.sshproxyproject.service.IUserService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ServerChannel;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import com.zhanghan.sshproxyproject.core.server.BackendManager.*;

import static com.zhanghan.sshproxyproject.common.Constants.ONLINE;

/*
* 1、监听线程将请求交付工作线程
* 2、工作线程调用代理服务端接受，校验请求会话
* */
@Component
@Slf4j
public class ListenManager {
    @Resource(name = "listenExecutor")
    private Executor listenExecutor;
    @Resource(name = "workExecutor")
    private Executor workThreadPool;
    @Resource
    private SshClient proxyClient;
    @Resource
    private ProxyForwarder proxyForwarder;
    @Resource
    private BackendManager backendManager;
    @Resource
    private IUserService userService;
    @Resource
    private ISysRoleService roleService;
    @Resource
    private LoginListener loginListener;


    public static Map<String,StringBuilder> userStringBuilders = new ConcurrentHashMap<>();


    public void handleRequest(InputStream userIn, OutputStream userOut, ServerChannel userChannel, User user){
        ServerSession userSession = userChannel.getServerSession();
        log.info("开始执行任务->ip:{}",userSession.getRemoteAddress());
        //将任务异步交付给工作线程池
        workThreadPool.execute(()->{
            boolean checkPass = connectToRealBackend(userIn,userOut,userChannel,user);
            if(!checkPass){
                log.error("代理端认证失败!");
                userSession.close(true);
            }
        });
    }

    //实现认证校验
    //TODO 实现传参(username,password)
    public boolean connectToRealBackend(InputStream userIn, OutputStream userOut,ServerChannel userChannel,User user){
        //获取用户->代理服务的对话
        ServerSession userSession = userChannel.getServerSession();
        log.info("代理服务端进行第二次进行认证校验->用户:{},ip:{}",user.getUsername(),userSession.getRemoteAddress());
        ClientSession toBackendSession = null;

        //轮询获取服务器
        BackendServer server = backendManager.select(backendManager.serverList);

        //通过名字查询对应的用户
        User proxyUser = userService.findByName(user.getUsername());

        //获取角色
        String role = proxyUser.getRole();
        SysRole proxyRole = roleService.getByRoleCode(role);

        if(proxyRole == null){
            log.error("不存在该角色!");
            throw new RuntimeException();
        }

        try {
            toBackendSession = proxyClient.connect(
                    proxyUser.getRole(),
                    server.getHost(),
                    server.getPort()
                    ).verify(5000)
                    .getSession();

            //验证后台，代理自动填密码登录
            //TODO 给密码加密
            toBackendSession.addPasswordIdentity(proxyRole.getPassword());
            toBackendSession.auth().verify(5000);

//            //密钥登录实现（代理->服务端）keyPair 密钥对
//            //拼接私钥路径 TODO 后期需要拼接后台服务器私钥
//            Path path = Paths.get("C:\\Users\\HP\\.ssh\\id_rsa");
//            Collection<KeyPair> keyPairs = null;
//            try {
//                //从path加载密钥对，解析成KeyPair对象
//                keyPairs = SecurityUtils.getKeyPairResourceParser().loadKeyPairs(null, path, FilePasswordProvider.EMPTY);
//            } catch (GeneralSecurityException e) {
//                log.error("第二层校验-密钥对加载失败");
//                throw new RuntimeException(e);
//            }
//
//            if(keyPairs == null){
//                log.error("密钥对为空");
//                return false;
//            }
//            //把加载出来的密钥对给对话进行校验
//            for (KeyPair kp : keyPairs) {
//                toBackendSession.addPublicKeyIdentity(kp);
//            }

//            toBackendSession.auth().verify(10000);

            log.info("{}->连接成功",userSession.getUsername());

            //添加对应的sessionid以及新建一个对应的字符缓冲区
            String sessionId = Base64.getEncoder().encodeToString(userSession.getSessionId());
            //如果不存在对应的对话id，则创建新的
            if(!userStringBuilders.containsKey(sessionId)) {
                userStringBuilders.put(sessionId, new StringBuilder(8192));
            }

            //封装sessionInfo,监听器将信息写入在线会话池
            SessionInfo sessionInfo = SessionInfo.builder()
                    .sessionId(sessionId)
                    .userId(proxyUser.getId())
                    .username(proxyUser.getUsername())
                    .clientIp(userSession.getRemoteAddress().toString())
                    .loginTime(LocalDateTime.now())
                    .lastActiveTime(LocalDateTime.now())
                    .status(ONLINE)
                    .serverId(server.getId())
                    .toBackendSession(toBackendSession)
                    .userSession(userSession)
                    .build();
            //写入会话池
            loginListener.addToOnlineSessionPool(sessionInfo,server.getId());


            //创建Channel shell通道，实现交互式终端,打开命令行窗口
            ChannelShell toBackendChannel = toBackendSession.createShellChannel();
            //打开终端
            toBackendChannel.open().verify(5000);

            log.info("后端channel终端打开成功");
//            userIn,userOut,userChannel, toBackendChannel,proxyUser,server
            //封装SessionContext，减少参数传递
            SessionContext sessionContext = SessionContext.builder()
                    .user(proxyUser)
                    .userIn(userIn)
                    .userOut(userOut)
                    .userChannel(userChannel)
                    .backendChannel(toBackendChannel)
                    .backendServer(server)
                    .build();

            //建立双向连接（用户 ↔ 代理 ↔ 后端）
            proxyForwarder.forward(sessionContext);

        } catch (IOException e) {
            log.error("代理服务器二次认证校验失败，创建channelShell终端失败");
            throw new RuntimeException(e);
        }
        return true;
    }

}
