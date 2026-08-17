package com.zhanghan.sshproxyproject.interceptor;

import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import io.netty.util.internal.StringUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.concurrent.ConcurrentHashMap;

/*
* 登录拦截器，检测token
* */
@Component
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private ConcurrentHashMap<String, LoginFormDTO> LOGIN_MESSAGE;


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取token
        String token = request.getHeader("authorization");
        // 浏览器原生 EventSource 无法自定义请求头，SSE 连接改用 ?token=xxx 传参
        if (StringUtil.isNullOrEmpty(token)) {
            token = request.getParameter("token");
        }
        //未授权，拦截
        if(StringUtil.isNullOrEmpty(token)){
            response.setStatus(401);
            return false;
        }
        //获取用户信息
        LoginFormDTO userDto = LOGIN_MESSAGE.get(token);
        if(userDto == null){
            response.setStatus(401);
            return false;
        }
        //保存信息
        UserHolder.saveUser(userDto);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 请求结束后清理 ThreadLocal，防止内存泄漏和用户串号
        UserHolder.removeUser();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

}
