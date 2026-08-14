package com.zhanghan.sshproxyproject;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SpringBootTest
@Slf4j
class SshProxyProjectApplicationTests {

    // ===================== 注入 SSH 连接配置 =====================
    @Value("${test.ssh.host}")
    private String sshHost;

    @Value("${test.ssh.port}")
    private int sshPort;

    @Value("${test.ssh.username}")
    private String sshUsername;

    @Value("${test.ssh.password}")
    private String sshPassword;

    @Value("${test.ssh.connect-timeout:5000}")
    private int connectTimeout;

    // ===================== 注入并发测试参数 =====================
    @Value("${test.concurrency.threads:50}")
    private int concurrencyThreads;

    @Value("${test.concurrency.total-requests:200}")
    private int totalRequests;

    @Value("${test.concurrency.duration-seconds:30}")
    private int durationSeconds;

    @Resource
    private SshClient client;

    /**
     * 当前测试中的线程池引用，用于 @PreDestroy 兜底清理
     */
    private volatile ExecutorService activePool;

    @Test
    void contextLoads() {
    }

    // ==================== 场景一：瞬时并发测试 ====================
    // 所有线程在 CountDownLatch 发令后同时发起 SSH 连接，测突发流量下的表现

    @Test
    void testBurstConcurrency() throws InterruptedException {
        log.info("========== 场景一：瞬时并发测试 ==========");
        log.info("配置: 并发线程={}, 总请求={}, 目标={}:{}",
                concurrencyThreads, totalRequests, sshHost, sshPort);

        CountDownLatch startLatch = new CountDownLatch(1);       // 发令枪
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(concurrencyThreads);
        activePool = pool;

        long testStart = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await(); // 等发令枪，所有线程同时起跑
                    long t1 = System.currentTimeMillis();
                    doOneLogin();
                    long t2 = System.currentTimeMillis();
                    latencies.add(t2 - t1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("SSH连接失败", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 发令！
        startLatch.countDown();
        log.info("发令枪已触发，{}个线程同时开始连接...", concurrencyThreads);

        // 等待所有完成，最多等 connectTimeout * 2 秒
        boolean allDone = doneLatch.await(connectTimeout * 2L, TimeUnit.MILLISECONDS);
        long testEnd = System.currentTimeMillis();
        pool.shutdownNow();
        activePool = null;

        // --- 输出统计 ---
        printBurstReport(successCount.get(), failCount.get(),
                totalRequests - successCount.get() - failCount.get(),
                latencies, testEnd - testStart, allDone);
    }

    // ==================== 场景二：持续负载测试 ====================
    // 固定并发数持续跑 N 秒，观察系统在持续压力下的稳定性和吞吐量

    @Test
    void testSustainedLoad() throws InterruptedException {
        log.info("========== 场景二：持续负载测试 ==========");
        log.info("配置: 并发线程={}, 持续时长={}s, 目标={}:{}",
                concurrencyThreads, durationSeconds, sshHost, sshPort);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        CyclicBarrier barrier = new CyclicBarrier(concurrencyThreads);
        CountDownLatch doneLatch = new CountDownLatch(concurrencyThreads);

        ExecutorService pool = Executors.newFixedThreadPool(concurrencyThreads);
        activePool = pool;

        long deadline = System.currentTimeMillis() + durationSeconds * 1000L;
        long testStart = System.currentTimeMillis();

        for (int i = 0; i < concurrencyThreads; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(); // 所有线程同时起跑
                    while (System.currentTimeMillis() < deadline) {
                        long t1 = System.currentTimeMillis();
                        try {
                            doOneLogin();
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        } finally {
                            latencies.add(System.currentTimeMillis() - t1);
                        }
                    }
                } catch (Exception e) {
                    log.error("压测线程异常", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 等待所有线程跑完，额外容忍 10 秒
        doneLatch.await(durationSeconds + 10, TimeUnit.SECONDS);
        long testEnd = System.currentTimeMillis();
        pool.shutdownNow();
        activePool = null;

        double actualDurationSec = (testEnd - testStart) / 1000.0;
        printSustainedReport(successCount.get(), failCount.get(), latencies, actualDurationSec);
    }

    // ==================== 场景三：递增加压测试 ====================
    // 线程数从 10 → 30 → 50 阶梯递增，找到系统瓶颈点

    @Test
    void testIncrementalLoad() throws InterruptedException {
        log.info("========== 场景三：递增加压测试 ==========");
        log.info("目标: {}:{}", sshHost, sshPort);

        int[] threadLevels = {10, 30, 50};
        int requestsPerLevel = 50;

        for (int threads : threadLevels) {
            log.info("--- 当前并发数: {} ---", threads);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(requestsPerLevel);
            ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger fail = new AtomicInteger(0);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            activePool = pool;

            for (int i = 0; i < requestsPerLevel; i++) {
                pool.submit(() -> {
                    try {
                        startLatch.await();
                        long t1 = System.currentTimeMillis();
                        doOneLogin();
                        latencies.add(System.currentTimeMillis() - t1);
                        success.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(connectTimeout * 3L, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            activePool = null;

            printLevelReport(threads, success.get(), fail.get(), latencies);
        }
    }

    // ==================== 核心：单次 SSH 连接 ====================

    private void doOneLogin() {
        try (ClientSession session = client.connect(sshUsername, sshHost, sshPort)
                .verify(connectTimeout)
                .getSession()) {
            session.addPasswordIdentity(sshPassword);
            session.auth().verify(connectTimeout);

            // ChannelShell 也要正确关闭
            try (ChannelShell channel = session.createShellChannel()) {
                channel.open().verify(connectTimeout);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 统计输出 ====================

    private void printBurstReport(int success, int fail, int timeout,
                                   ConcurrentLinkedQueue<Long> latencies,
                                   long elapsedMs, boolean allDone) {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        double rate = (double) success /totalRequests * 100;

        log.info("========== 瞬时并发测试结果 ==========");
        log.info("全部完成: {}, 总耗时: {}ms", allDone, elapsedMs);
        log.info("成功: {}, 失败: {}, 超时未完成: {},成功率: {}", success, fail, timeout,rate);
        if (!sorted.isEmpty()) {
            log.info("延迟(ms) → 最小:{}, 最大:{}, 平均:{:.0f}, P50:{}, P95:{}, P99:{}",
                    sorted.get(0),
                    sorted.get(sorted.size() - 1),
                    sorted.stream().mapToLong(Long::longValue).average().orElse(0),
                    percentile(sorted, 50),
                    percentile(sorted, 95),
                    percentile(sorted, 99));
        }
        if (elapsedMs > 0) {
            log.info("QPS: {:.1f}", success * 1000.0 / elapsedMs);
        }
    }

    private void printSustainedReport(int success, int fail,
                                       ConcurrentLinkedQueue<Long> latencies,
                                       double durationSec) {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        double rate = (double) success / concurrencyThreads;

        log.info("========== 持续负载测试结果 ==========");
        log.info("实际运行: {:.1f}s, 成功: {}, 失败: {}，成功率: {}", durationSec, success, fail,rate);
        if (!sorted.isEmpty()) {
            log.info("延迟(ms) → 最小:{}, 最大:{}, 平均:{:.0f}, P50:{}, P95:{}, P99:{}",
                    sorted.get(0),
                    sorted.get(sorted.size() - 1),
                    sorted.stream().mapToLong(Long::longValue).average().orElse(0),
                    percentile(sorted, 50),
                    percentile(sorted, 95),
                    percentile(sorted, 99));
        }
        if (durationSec > 0) {
            log.info("吞吐量 QPS: {:.1f}", success / durationSec);
        }
    }

    private void printLevelReport(int threads, int success, int fail,
                                   ConcurrentLinkedQueue<Long> latencies) {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        log.info("线程数={} → 成功:{}, 失败:{}, 平均延迟:{:.0f}ms, P95:{}ms",
                threads, success, fail,
                sorted.isEmpty() ? 0 : sorted.stream().mapToLong(Long::longValue).average().orElse(0),
                sorted.isEmpty() ? "N/A" : String.valueOf(percentile(sorted, 95)));
    }

    /**
     * 计算延迟百分位
     */
    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    // ==================== 资源清理 ====================

    @AfterEach
    void cleanupAfterTest() {
        if (activePool != null) {
            activePool.shutdownNow();
            activePool = null;
        }
    }

    @PreDestroy
    void shutdown() {
        // 1. 关闭可能残留的压测线程池
        if (activePool != null) {
            activePool.shutdownNow();
            log.info("压测线程池已关闭");
        }
        // 2. 关闭全局 SSH 客户端
        if (client != null) {
            try {
                client.stop();
                log.info("SshClient客户端已关闭");
            } catch (Exception e) {
                log.error("关闭SshClient失败", e);
            }
        }
    }
}
