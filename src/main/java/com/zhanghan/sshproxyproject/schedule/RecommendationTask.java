package com.zhanghan.sshproxyproject.schedule;

import com.zhanghan.sshproxyproject.service.RecommendationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
* 定时任务：每天 0 点刷新 AI 策略建议
* 数据读取（状态分布 list + 危险命令池 map + 危险排行）与 AI 生成逻辑
* 统一放在 RecommendationService 中，由 recommendExecutor 线程池异步执行
* */
@Component
@Slf4j
public class RecommendationTask {

    @Resource
    private RecommendationService recommendationService;

    @Scheduled(cron = "0 0 0 * * *")
    public void refreshRecommendation(){
        recommendationService.refresh();
    }
}
