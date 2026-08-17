package com.zhanghan.sshproxyproject.controller;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.service.IUserService;
import com.zhanghan.sshproxyproject.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "用户管理", description = "用户列表查询")
@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Operation(summary = "获取用户列表")
    @GetMapping("/list")
    public Result showUsers(){
        log.info("展示用户列表");
        List<User> users = userService.list();
        List<UserVO> userVOS = new ArrayList<>();

        for(User user:users){
            UserVO userVO = UserVO.builder()
                    .id(user.getId())
                    .role(user.getRole())
                    .createTime(user.getCreateTime())
                    .status(user.getStatus())
                    .username(user.getUsername())
                    .password("******")
                    .build();
            userVOS.add(userVO);
        }

        long count = userService.count();

        return Result.ok(userVOS,count);
    }

}
