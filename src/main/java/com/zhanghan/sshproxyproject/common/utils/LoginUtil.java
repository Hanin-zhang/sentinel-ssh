package com.zhanghan.sshproxyproject.common.utils;

import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.util.Collections;

@Slf4j
@Component
public class LoginUtil {

    @Resource
    private IUserService userService;

    //登录校验
    public  boolean loginByPassword(String username,String password){

        try {
            User user = userService.findByName(username);

            //判空
            if(user == null){
                log.error("不存在该用户");
                return false;
            }

            if(user.getStatus() == 0){
                return false;
            }

            //密码正确
            if(password.equals(user.getPassword()) && username.equals(user.getUsername())){
                log.info("{}登录成功",username);
                return true;
            }

            log.warn("密码错误");
            return false;
        } catch (Exception e) {
            log.error("认证异常",e);
            return false;
        }
    }

    //密钥登录
    public boolean loginByKey(String username, PublicKey key){
        User user = userService.findByName(username);

        //判空
        if(user == null){
            return false;
        }

        try {
            //获取公钥
            String publicKey = user.getPublicKey();
            PublicKeyEntry entry = PublicKeyEntry.parsePublicKeyEntry(publicKey);
            //转化成mina sshd需要的对象
            PublicKey minaPublicKey = entry.resolvePublicKey(null, Collections.emptyMap(), null);

            //该公钥和数据库的是否一致
            boolean match = KeyUtils.compareKeys(key, minaPublicKey);

            log.info("用户->代理层：用户:{}-密钥认证结果:{}",username,match);
            return match;
        } catch (Exception e) {
            log.error("用户->代理层：公钥认证失败认证",e);
            return false;
        }
    }
}
