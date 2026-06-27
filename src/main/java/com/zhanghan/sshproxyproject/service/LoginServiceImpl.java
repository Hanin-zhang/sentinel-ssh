package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.common.utils.LoginUtil;
import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LoginServiceImpl implements  ILoginService{

    @Resource
    private IUserService userService;
    @Resource
    private LoginUtil loginUtil;
    @Resource
    private ConcurrentHashMap<String,LoginFormDTO> LOGIN_MESSAGE;

    /*
    * 用户登录
    * */
    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        String username = loginFormDTO.getUsername();

        if (username == null || username.equals(" ")) {
            return Result.fail("用户名不能为空！");
        }
        String password = loginFormDTO.getPassword();
//        MultipartFile key = loginFormDTO.getPrivateKey();

        if (password == null) {
            return Result.fail("密码不能为空");
        }

        boolean login = loginUtil.loginByPassword(username, password);
        if (!login) {
            return Result.fail("登录失败");
        }

        //校验成功
        //生成token，返回
        String token = UUID.randomUUID().toString();
        //存储loginFormDTO进用户池，为保证拦截器内的用户唯一
        LOGIN_MESSAGE.put(token,loginFormDTO);

        return Result.ok(token);
    }
}
