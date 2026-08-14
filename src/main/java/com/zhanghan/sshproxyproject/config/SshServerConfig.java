package com.zhanghan.sshproxyproject.config;

import com.zhanghan.sshproxyproject.common.utils.LoginUtil;
import com.zhanghan.sshproxyproject.core.proxy.ListenManager;
import com.zhanghan.sshproxyproject.core.proxy.ProxyShellCommand;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.listener.LoginListener;
import com.zhanghan.sshproxyproject.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.ProcessShellFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static com.zhanghan.sshproxyproject.common.Constants.ServerKeyPath;

@Configuration
@Slf4j
public class SshServerConfig {

    @Resource
    private ListenManager listenManager;
    @Resource
    private Executor workExecutor;
    @Resource
    private IUserService userService;
    @Resource
    private LoginListener loginListener;
    @Resource
    private LoginUtil loginUtil;

    private final User user = new User();

    // 配置52020端口的代理服务器
    @Bean(destroyMethod = "stop")
    public SshServer proxyServer() {
        try {
            SshServer server = SshServer.setUpDefaultServer();
            server.setPort(52020);
            server.setHost("0.0.0.0");


            Path keyDir = Paths.get(ServerKeyPath);
            if (!Files.exists(keyDir)) {
                Files.createDirectories(keyDir);
                log.info("密钥文件目录创建成功：{}", keyDir.toAbsolutePath());
            }

            server.setKeyPairProvider(
                    new SimpleGeneratorHostKeyProvider(keyDir.resolve("proxyHostKey.ser"))
            );

            // 会话监听
            server.addSessionListener(loginListener);

            //实现登录,密码登录
            server.setPasswordAuthenticator(
                    (username, password, session) -> {
                        user.setUsername(username);
                        user.setPassword(password);
                        return loginUtil.loginByPassword(username,password);
                    }
            );

//            // 密钥认证登录
//            //框架帮我们校验签名，自动提取客户端的公钥
//            server.setPublickeyAuthenticator(
//                    (username, key, session) -> {
//                        user.setUsername(username);
//                        return loginUtil.loginByKey(username,key);
//                    }
//            );

            //构建代理shell
            server.setShellFactory(channel -> {
                return new ProxyShellCommand(listenManager,workExecutor,user);
            });

            server.start();
            log.info("代理服务器启动！端口:52020");
            return server;
        } catch (IOException e) {
            log.error("代理服务器启动异常!!", e);
            throw new RuntimeException(e);
        }
    }


}