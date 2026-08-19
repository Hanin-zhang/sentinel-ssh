package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhanghan.sshproxyproject.dto.RegisterDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.entity.UserDTO;
import com.zhanghan.sshproxyproject.vo.CodeLimitResult;

public interface IUserService extends IService<User> {
    User findByName(String username);

    /**
     * 判断用户名是否已存在（供前端失焦查重）
     */
    boolean usernameExists(String username);

    Result addUser(UserDTO userDTO);

    Result registerByCode(RegisterDTO registerDTO);

    CodeLimitResult sendCode(String mail);

    boolean emailExists(String email);
}
