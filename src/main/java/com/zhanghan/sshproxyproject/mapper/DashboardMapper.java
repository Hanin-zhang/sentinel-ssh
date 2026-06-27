package com.zhanghan.sshproxyproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanghan.sshproxyproject.entity.DashboardData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.concurrent.atomic.AtomicLong;

@Mapper
public interface DashboardMapper extends BaseMapper<DashboardData> {

    @Select("select total_cmd_num from dashboard_stat where id = 1")
    long getTotalCmdNum();

    @Select("select total_danger_cmd_num from dashboard_stat where id = 1")
    long getTotalDangerCmdNum();
}
