package com.zhanghan.sshproxyproject.controller;

import ch.qos.logback.core.joran.util.beans.BeanUtil;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.entity.UserDTO;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import com.zhanghan.sshproxyproject.service.IUserService;
import com.zhanghan.sshproxyproject.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "用户管理", description = "用户列表查询")
@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;
    @Autowired
    private UserMapper userMapper;

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

    @Operation(summary = "新增用户")
    @PostMapping("/add")
    public Result addUser(@RequestBody UserDTO userDTO,@RequestParam String adminPassword){
            log.info("新增用户{}",userDTO);
            return userService.addUser(userDTO,adminPassword);
    }

    @Operation(summary = "校验用户名是否已存在（新增用户时失焦查重）")
    @GetMapping("/exist")
    public Result checkUsername(@RequestParam String username){
        if (!StringUtils.hasText(username)) {
            return Result.fail("用户名不能为空");
        }
        boolean exists = userService.usernameExists(username.trim());
        return Result.ok(exists);
    }


}
