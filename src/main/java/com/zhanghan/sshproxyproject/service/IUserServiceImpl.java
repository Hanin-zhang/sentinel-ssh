package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.entity.UserDTO;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class IUserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Value("${adminPassword}")
    private String adminPermission;

    @Override
    public User findByName(String username) {
            return userMapper.findByName(username);
    }

    @Override
    public boolean usernameExists(String username) {
        return userMapper.findIfHavingUser(username) != null;
    }

    @Override
    public Result addUser(UserDTO userDTO, String adminPassword) {
        //基础校验：null / 空串 / 纯空格都拦截（原只挡 null）
        if (userDTO == null || !StringUtils.hasText(userDTO.getUsername()) || !StringUtils.hasText(userDTO.getPassword())) {
            return Result.fail("名称或密码不能为空！！");
        }
        String username = userDTO.getUsername().trim();
        String password = userDTO.getPassword();

        //先验权再查重：未授权的调用方无法通过"该用户名已存在"枚举用户名
        if (!StringUtils.hasText(adminPassword)) {
            return Result.fail("验权密码不能为空！！");
        }
        if (!adminPassword.equals(adminPermission)) {
            return Result.fail("验权密码错误！！");
        }

        //再判断角色是否合法
        String role = userDTO.getRole();
        if (role == null || role.equals("admin")) {
            return Result.fail("角色不合法！！，只能（guest/ops）");
        }

        //最后判断用户名是否已存在
        Long id = userMapper.findIfHavingUser(username);
        //数据库索引优化，将名字设定为唯一索引
        if (id != null) {
            return Result.fail("该用户名已存在");
        }

        //都通过，方可添加用户
        User user = User.builder()
                .username(username)
                .password(password)
                .role(role)
                .status(1)
                .DangerTotalNum(0L)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        boolean saved = save(user);
        if (!saved) {
            return Result.fail("添加新用户失败");
        }
        return Result.ok();
    }


}
