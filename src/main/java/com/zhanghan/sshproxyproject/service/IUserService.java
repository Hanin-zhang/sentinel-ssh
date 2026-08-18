package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.entity.UserDTO;

public interface IUserService extends IService<User> {
    User findByName(String username);

    /**
     * 判断用户名是否已存在（供前端失焦查重）
     */
    boolean usernameExists(String username);

    Result addUser(UserDTO userDTO, String adminPassword);
}
