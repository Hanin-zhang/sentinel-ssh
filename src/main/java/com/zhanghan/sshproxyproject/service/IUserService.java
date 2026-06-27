package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhanghan.sshproxyproject.entity.User;

public interface IUserService extends IService<User> {
    User findByName(String username);

}
