package com.zhanghan.sshproxyproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BackendServerMapper extends BaseMapper<BackendServer> {

    @Update("update `sshproxy-project`.backend_server set connection_count = connection_count + 1 where id = #{serverId}")
    void updateServerNum(Integer serverId);

    @Update("update `sshproxy-project`.backend_server set connection_count = connection_count - 1 where id = #{serverId}")
    void cutServerNum(Integer serverId);
}
