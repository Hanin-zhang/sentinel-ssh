package com.zhanghan.sshproxyproject.service;

import ch.qos.logback.core.testUtil.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhanghan.sshproxyproject.common.utils.EmailUtil;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.dto.RegisterDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.User;
import com.zhanghan.sshproxyproject.entity.UserDTO;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import com.zhanghan.sshproxyproject.vo.CodeLimitResult;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.zhanghan.sshproxyproject.common.Constants;
import com.zhanghan.sshproxyproject.common.utils.CaffeineUtil;
import com.zhanghan.sshproxyproject.common.utils.EmailValidateUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class IUserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private ConcurrentHashMap<String, LoginFormDTO> LOGIN_MESSAGE;

    @Resource
    private UserMapper userMapper;

    @Value("${adminPassword}")
    private String adminPermission;

    @Resource
    private CaffeineUtil caffeineUtil;

    @Override
    public User findByName(String username) {
            return userMapper.findByName(username);
    }

    @Override
    public boolean usernameExists(String username) {
        return userMapper.findIfHavingUser(username) != null;
    }

    @Override
    public Result addUser(UserDTO userDTO) {
        //基础校验：null / 空串 / 纯空格都拦截（原只挡 null）
        if (userDTO == null || !StringUtils.hasText(userDTO.getUsername()) || !StringUtils.hasText(userDTO.getPassword())) {
            return Result.fail("名称或密码不能为空！！");
        }
        String username = userDTO.getUsername().trim();
        String password = userDTO.getPassword();
        String adminPassword = userDTO.getAdminPassword();

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
                .email(userDTO.getEmail())
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

    /*
    * 注册（邮箱验证码注册，无需管理员验权密码）
    * 两步共用本接口：
    *   第一步：只传 email + code → 校验验证码通过即返回成功，前端据此跳转填资料页
    *   第二步：连同 username / password 再调一次 → 二次校验验证码 + 创建用户（角色强制 guest）
    * 说明：注册的授权是"拥有该邮箱"，与 addUser 的管理员暗号是两套信任模型，不能复用 addUser
    * */
    @Override
    public Result registerByCode(RegisterDTO registerDTO) {
        // ① 基础判空
        if (registerDTO == null || !StringUtils.hasText(registerDTO.getEmail()) || !StringUtils.hasText(registerDTO.getCode())) {
            return Result.fail("邮箱或验证码不能为空！");
        }
        String email = registerDTO.getEmail().trim();
        String userCode = registerDTO.getCode().trim();

        // ② 校验验证码（拥有邮箱 = 注册授权）
        if (!caffeineUtil.checkCodeIfRight(email, userCode)) {
            return Result.fail("验证码不正确或已过期！");
        }

        //已存在该邮箱 → 邮箱+验证码正确即视为登录成功（直登，无需密码）
        // ④ 查重：邮箱
        if (userMapper.findIfHavingUserByEmail(email) != null) {
            return loginByemail(email);
        }

        //没有使用过，接着注册
        // ③ 只有 email+code，属于第一步验证码校验，通过即返回
        String username = registerDTO.getUsername() == null ? "" : registerDTO.getUsername().trim();
        String password = registerDTO.getPassword();
        if (!StringUtils.hasText(username) && !StringUtils.hasText(password)) {
            return Result.ok();
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Result.fail("用户名或密码不能为空！");
        }

        if (userMapper.findIfHavingUser(username) != null) {
            return Result.fail("该用户名已存在");
        }

        // ⑤ 创建用户：自注册强制 guest 角色
        User user = User.builder()
                .username(username)
                .password(password)
                .role(Constants.MYGUEST)
                .status(1)
                .email(email)
                .DangerTotalNum(0L)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        return save(user) ? Result.ok() : Result.fail("注册失败，请稍后重试");
    }

    private Result loginByemail(String email) {

        //只查 username + status（password 用不到，不从库读出），由 selectByEmail 返回
        LoginFormDTO found = userMapper.selectByEmail(email);

        //账号不存在（理论上不会走到，防御一次查询）
        if (found == null || !StringUtils.hasText(found.getUsername())) {
            return Result.fail("该账号不存在");
        }

        //账号被禁用（status 1正常 0禁用），与密码登录 LoginUtil 的校验保持一致
        if (found.getStatus() != null && found.getStatus() == 0) {
            log.warn("用户邮箱{}-账号已被禁用，拒绝登录", email);
            return Result.fail("该账号已被禁用，无法登录");
        }

        //校验成功：生成token，存 username 进用户池（拦截器按 username 认人）
        String token = UUID.randomUUID().toString();
        LoginFormDTO login = LoginFormDTO.builder().username(found.getUsername()).build();
        LOGIN_MESSAGE.put(token, login);
        log.info("用户邮箱{}-登录成功", email);

        //把 token + 账号用户名一起返回，前端据此完成"验证码即登录"体验
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", login.getUsername());
        return Result.ok(data);
    }

    /*
    * 发送验证码
    * 用caffeine作为本地内存缓存，
    * 同时可以利用它的自动过期和容量淘汰能力，做验证码缓存、短期状态保存以及单机限流。
    * */
    @Override
    public CodeLimitResult sendCode(String mail) {

        if(!EmailValidateUtil.isValidEmail(mail)){
            return CodeLimitResult.deny("邮箱格式错误！！");
        }

        //生成验证码
        String code = RandomStringUtils.randomNumeric(6);

        return caffeineUtil.tryAcquire(mail,code);
    }

    @Override
    public boolean emailExists(String email) {
        return userMapper.findIfHavingUserByEmail(email) != null;
    }

}
