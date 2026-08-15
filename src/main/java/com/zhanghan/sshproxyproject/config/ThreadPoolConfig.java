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

    /**
     * AI 审查 + 告警异步线程池
     * <p>
     * 专门处理 Phase 2 的异步 AI 审查和 HIGH/MEDIUM 告警写入，
     * 与 IO 线程池隔离，避免 AI 调用超时影响 SSH 数据转发。
     * <p>
     * 参数说明：
     * <ul>
     *   <li>core=2：最少保留 2 个线程待命（AI 调用频率不高但需快速响应）</li>
     *   <li>max=8：最多 8 个并发 AI 审查 + 告警写入</li>
     *   <li>队列容量=200：缓冲高峰期的灰区命令</li>
     *   <li>CallerRunsPolicy：队列满时回退到调用线程执行（不丢任务）</li>
     * </ul>
     */
    @Bean("alertExecutor")
    public Executor alertExecutor() {
        return new ThreadPoolExecutor(
                2,
                8,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(200),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * AI 策略建议生成线程池
     * <p>
     * 定时任务每天 0 点触发，读取审计统计数据并调用 DeepSeek 生成安全策略建议。
     * 频率极低（一天一次），池子给最小即可，避免与其他业务线程池抢占资源。
     * <p>
     * 参数说明：
     * <ul>
     *   <li>core=1 / max=2：单次任务足够</li>
     *   <li>队列容量=10：缓冲极端情况下的一次性并发</li>
     *   <li>CallerRunsPolicy：队列满时回退到调度线程执行（不丢任务）</li>
     * </ul>
     */
    @Bean("recommendExecutor")
    public Executor recommendExecutor() {
        return new ThreadPoolExecutor(
                1,
                2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(10),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
