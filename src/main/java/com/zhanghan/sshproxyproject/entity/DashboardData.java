package com.zhanghan.sshproxyproject.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardData {

    //在线用户
    private Integer onlineNum;

    //总用户数量
    private Long totalUserNum;

    //今日命令数量
    private Long todayCmdNum;

    //今日危险命令数量
    private Long todayDangerCmdNum;

    private AtomicLong totalCmdNum;

    private AtomicLong totalDangerCmdNum;
}
