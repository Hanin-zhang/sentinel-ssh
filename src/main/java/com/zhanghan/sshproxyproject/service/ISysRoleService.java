package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhanghan.sshproxyproject.entity.SysRole;

public interface ISysRoleService extends IService<SysRole> {
    SysRole getByRoleCode(String role);
}
