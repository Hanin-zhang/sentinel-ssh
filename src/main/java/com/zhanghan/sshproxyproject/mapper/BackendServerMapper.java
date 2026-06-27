package com.zhanghan.sshproxyproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BackendServerMapper extends BaseMapper<BackendServer> {
}
