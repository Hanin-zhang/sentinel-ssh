package com.zhanghan.sshproxyproject.dto;

import lombok.Data;

/*
* 用于邮箱注册新用户
* */
@Data
public class RegisterDTO {

    //邮箱
    private String email;

    //验证码
    private String code;

    //用户名（第二步填资料时提交）
    private String username;

    //密码（第二步填资料时提交）
    private String password;
}
