package com.zhanghan.sshproxyproject.entity;

import lombok.Data;

import static com.zhanghan.sshproxyproject.common.Constants.MYGUEST;

/*
* 注册、新增新用户
* */
@Data
public class UserDTO {

    private String username;

    private String password;

    //默认为游客
    private String role = MYGUEST;

    private String email;

    //验证权限密码
    private String adminPassword;
}
