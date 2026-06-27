package com.zhanghan.sshproxyproject.common.utils;

import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/*
* 设置本地线程，保证信息安全
* */
@Slf4j
public class UserHolder {

    private static final ThreadLocal<LoginFormDTO> t1 = new ThreadLocal<>();

    public static void saveUser(LoginFormDTO loginFormDTO){
            t1.set(loginFormDTO);
    }

    public static LoginFormDTO getUser(){
        return t1.get();
    }

    public static void removeUser(){
        t1.remove();
    }
}
