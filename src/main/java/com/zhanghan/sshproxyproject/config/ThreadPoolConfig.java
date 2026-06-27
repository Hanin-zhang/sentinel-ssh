package com.zhanghan.sshproxyproject.config;

import com.zhanghan.sshproxyproject.core.proxy.ListenManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.session.ServerSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
@Slf4j
public class ThreadPoolConfig {

    //监听线程池，负责看是否有新连接
    @Bean("listenExecutor")
    public Executor listenExecutor() {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(50),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    //工作线程，负责与后台建立连接
    @Bean("workExecutor")
    public Executor workExecutor() {
        return new ThreadPoolExecutor(
                8,
                20,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(600),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    //IO长任务线程，负责与后台建立连接
    @Bean("ioExecutor")
    public Executor ioExecutor() {
        return new ThreadPoolExecutor(
                10,
                30,
                120L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(1000),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
