package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class IUserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User findByName(String username) {
            return userMapper.findByName(username);
    }

}
