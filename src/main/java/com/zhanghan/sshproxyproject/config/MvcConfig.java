package com.zhanghan.sshproxyproject.config;

import com.zhanghan.sshproxyproject.interceptor.LoginInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;

    //登录拦截器，实现每次访问前都经过该拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/auth/**",                 //登录接口，放行
                        "/v3/api-docs/**",          //Swagger 接口文档 JSON
                        "/swagger-ui/**",           //Swagger UI 页面
                        "/swagger-ui.html"
                )
                .order(0);
    }
}
