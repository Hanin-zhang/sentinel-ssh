package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhanghan.sshproxyproject.entity.SysRole;
import com.zhanghan.sshproxyproject.mapper.SysRoleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService{

    @Resource
    private SysRoleMapper roleMapper;

    @Override
    public SysRole getByRoleCode(String role) {
        return roleMapper.getByRoleCode(role);
    }
}
