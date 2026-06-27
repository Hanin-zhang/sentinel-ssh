package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import jakarta.servlet.http.HttpSession;

public interface ILoginService {
    Result login(LoginFormDTO loginFormDTO, HttpSession session);
}
