package com.zhanghan.sshproxyproject.dto;

import lombok.Data;

/*
* 用于邮箱注册新用户
* */
@Data
public class RegisterDTO {

    //邮箱
    private String mail;

    //验证码
    private String code;
}
