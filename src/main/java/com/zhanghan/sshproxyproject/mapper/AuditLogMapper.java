package com.zhanghan.sshproxyproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanghan.sshproxyproject.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    @Select("select count(*) from `sshproxy-project`.audit_log where DATE(create_time) = #{today}")
    Long countByToday(LocalDate today);

    @Select("select COUNT(*) from audit_log")
    long getTotalCmdNum();

    @Select("select COUNT(*) from audit_log where status = 1")
    long getTotalDangerCmdNum();

    // 统计某个用户今日命令总数
    @Select("select count(*) from audit_log where username = #{username} and DATE(create_time) = #{today}")
    Long countByUserAndToday(String username, LocalDate today);

    // 统计某个用户今日危险命令数
    @Select("select count(*) from audit_log where username = #{username} and status = 1 and DATE(create_time) = #{today}")
    Long countDangerByUserAndToday(String username, LocalDate today);

    // 统计某个用户的总命令数
    @Select("select count(*) from audit_log where username = #{username}")
    Long countByUser(String username);

    // 统计某个用户的总危险命令数
    @Select("select count(*) from audit_log where username = #{username} and status = 1")
    Long countDangerByUser(String username);

    // 危险命令排行榜（按用户分组统计危险命令数）
    @Select("select username, count(*) as danger_count from audit_log where status = 1 group by username order by danger_count desc limit 10")
    List<Map<String, Object>> getDangerRanking();
}
