package com.zhanghan.sshproxyproject.core.proxy;

import com.zhanghan.sshproxyproject.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

/*
* 代理shell，为解决clientSession没有输入输出流
* */
@Slf4j
public class ProxyShellCommand implements Command {

    private Executor workThreadPool;

    private ListenManager listenManager;

    private InputStream in;

    private OutputStream out;

    private OutputStream err;

    private final User user;

    public ProxyShellCommand(ListenManager listenManager, Executor workExecutor, User user) {
        this.listenManager = listenManager;
        this.workThreadPool = workExecutor;
        this.user = user;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {

    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void start(ChannelSession channel, Environment env) throws IOException {
        log.info("代理shell启动");

        try {
            workThreadPool.execute(() -> {
                listenManager.handleRequest(in, out, channel, user);
            });
        } catch (Exception e) {
            log.error("代理异常", e);
        }

    }

    @Override
    public void destroy(ChannelSession channel) throws Exception {

    }
}
