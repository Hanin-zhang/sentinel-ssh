package com.zhanghan.sshproxyproject.common.utils;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhanghan.sshproxyproject.vo.CodeLimitResult;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * 构造本地缓存实现对邮箱的限制
 * 请求发送验证码
        ↓
检查1分钟限制
        ├── 已存在 → 返回剩余秒数
        ↓
不存在 → 写入过期时间
        ↓
检查10分钟5次
        ├── 超过5次 → 拒绝
        ↓
允许发送
 * */
@Component
@Slf4j
public class CaffeineUtil {

    @Resource
    private EmailUtil emailUtil;

    // ========== 常量统一配置 ==========
    private static final int MAX_QUOTA = 5;                // 10分钟最多5次
    private static final long QUOTA_WINDOW_MIN = 10;  // 额度窗口：10分钟
    private static final long COOL_DOWN_SECONDS = 60;    // 单次冷却：1分钟
    private static final int MAX_CACHE_SIZE = 20_000;      // 缓存最大容量

    public final Cache<String, AtomicInteger> emailRateCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(QUOTA_WINDOW_MIN, TimeUnit.MINUTES)
            .build();

    public final Cache<String, Long> oneMinuteCache = Caffeine.newBuilder()
            .expireAfterWrite(COOL_DOWN_SECONDS, TimeUnit.SECONDS)
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    /*
     * 每十分钟5次(CAS)
     *发送额度
     * */
    private boolean allowSendQuota(String email) {

        AtomicInteger count = emailRateCache.get(email, K -> new AtomicInteger(0));

        while (true) {
            int cur = count.get();

            if (cur >= MAX_QUOTA) {
                return false;
            }

            if (count.compareAndSet(cur, cur + 1)) {
                return true;
            }
        }
    }

    /*
     * 每分钟一次
     * */
    private long checkPerMinuteLimit(String email) {
        // putIfAbsent：key不存在，写入true，返回null；
        //              存在返回旧值true
        //第一次存入
        long expireAt = System.currentTimeMillis() + 60_000;
        Long oldExpireAt = oneMinuteCache.asMap()
                .putIfAbsent(email, expireAt);

        //如果为空，为第一次发送,无需等待
        return oldExpireAt == null ? 0 : oldExpireAt;
    }


    public CodeLimitResult tryAcquire(String email,String code) {

        // 判断一分钟冷却
        long expireAt = checkPerMinuteLimit(email);
        if (expireAt != 0) {
            //截止时间减去现在的时间，就是等待时间
            long waitTime =
                    Math.max(
                            0,
                            (expireAt - System.currentTimeMillis()) / 1000
                    );
            return CodeLimitResult.deny("请" + waitTime + "秒后再重试", waitTime);
        }

        // 判断10分钟额度
        if (!allowSendQuota(email)) {
            return CodeLimitResult.deny("10分钟内验证码发送次数已达上限");
        }

        //允许放行
        //发送验证码
        //由邮箱发送验证码
        try {
            emailUtil.sendCodeMail(email,code);
        } catch (MessagingException e) {
            log.error("发送验证码异常");
            throw new RuntimeException(e);
        }
        return CodeLimitResult.allow();
    }

}
