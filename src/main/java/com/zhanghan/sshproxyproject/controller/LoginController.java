package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.service.ILoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;

@Tag(name = "认证", description = "登录 / 退出")
@RestController
@Slf4j
@RequestMapping("/auth")
public class LoginController {

    @Resource
    private ILoginService loginService;

    @Resource
    private ConcurrentHashMap<String, LoginFormDTO> LOGIN_MESSAGE;

    @Operation(summary = "登录", description = "账号密码登录，成功返回 token")
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO, HttpSession session){
        log.info("{}用户-登录",loginFormDTO.getUsername());
        return loginService.login(loginFormDTO,session);
    }

    @Operation(summary = "退出登录", description = "清除服务端 token")
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        // 从请求头获取 token 并清理
        String token = request.getHeader("authorization");
        if (token != null) {
            LoginFormDTO removed = LOGIN_MESSAGE.remove(token);
            if (removed != null) {
                log.info("用户{}-退出", removed.getUsername());
            }
        }
        UserHolder.removeUser();
        return Result.ok();
    }

}
