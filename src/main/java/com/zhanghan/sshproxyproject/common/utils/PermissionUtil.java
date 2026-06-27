package com.zhanghan.sshproxyproject.common.utils;

import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.service.IUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import com.zhanghan.sshproxyproject.common.Constants;
import org.springframework.stereotype.Component;

import static com.zhanghan.sshproxyproject.common.Constants.*;

/*
* 权限校验（判断命令是否运行执行）
* SSH会话方法（获取IP，存用户信息，取角色）
* 角色判断
* */
@Slf4j
public class PermissionUtil {

    //定义角色可执行的命令
    private static final Set<String> ADMINCOMMAND = Set.of("*");
    private static final Set<String> OPERATORCOMMAND = Set.of("exit","ls","cd","cat","pwd","echo");
    private static final Set<String> GUESTCOMMAND = Set.of("exit","ls","pwd","cd");


    //通过角色判断该命令是否能使用
    public static boolean checkUserPermission(User user,String command){
        if(command == null || command.isBlank()){
            return false;
        }
        String role = user.getRole();
        // 只取命令第一个词（如 rm -rf → rm）
        String cmd = command.trim().split("\\s+")[0];
        log.info("检查的语句,{}:{}",command,cmd);
        return switch (role){
            case MYADMIN -> true;
            case MYOPERATOR -> OPERATORCOMMAND.contains(cmd);
            case MYGUEST -> GUESTCOMMAND.contains(cmd);
            default -> false;
        };
    }

}
