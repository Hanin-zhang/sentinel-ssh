package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.common.utils.UserHolder;
import com.zhanghan.sshproxyproject.core.server.BackendManager;
import com.zhanghan.sshproxyproject.dto.LoginFormDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.*;
import com.zhanghan.sshproxyproject.mapper.SysRoleMapper;
import com.zhanghan.sshproxyproject.mapper.UserMapper;
import io.micrometer.common.util.StringUtils;
import io.netty.util.internal.StringUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class TerminalServerImpl implements TerminalServerce{

    @Resource
    private SshClient proxyClient;
    @Resource
    private IUserService userService;
    @Resource
    private IBackendServerService backendServerService;
    @Resource
    private ISysRoleService sysRoleService ;
    @Resource(name = "listenExecutor")
    private Executor listenExecutor;
    @Resource(name = "workExecutor")
    private Executor workThreadPool;
    @Resource
    private ConcurrentHashMap<String, TerminalSession> ONLINE_SESSION;
    @Resource
    private BackendManager backendManager;


    //创建一个会话，连接服务器
    @Override
    public Result connectToServer(Integer serverId, HttpServletRequest request) {
        //TODO获取用户信息//暂时伪造一份loginFormInfo
        LoginFormDTO loginFormDTO =
                LoginFormDTO.builder()
                        .username("Hanin")
                        .password("18389898069ZH@necpu")
                        .build();
        log.info("登录信息{}", loginFormDTO);

        User user = userService.findByName(loginFormDTO.getUsername());
        //判空
        if(user==null){
            return Result.fail("用户未登录!");
        }
        //获取代理角色的信息
        SysRole proxyRole = sysRoleService.getByRoleCode(user.getRole());
        //判空
        if(proxyRole==null){
            return Result.fail("后台代理用户不存在!");
        }
        BackendServer server;
        if(serverId!=null) {
            //获取服务器相关信息
            server = backendServerService.getById(serverId);
            //判空
            //如果不存在或者离线状态，不能登录
            if (server == null || !server.getOnline()) {
                return Result.fail("服务器出了会小差，暂时不能登录!");
            }
        }else {
            //否则默认采用轮询
            server = backendManager.select(backendManager.serverList);
        }

        //获取用户ip
        String userIp = request.getRemoteAddr();

        //创建会话
        ClientSession session = createSession(proxyRole, server);
        String sessionId = UUID.randomUUID().toString();

        //保存会话到map
        saveSessionToMap(serverId, session, sessionId, user,userIp);

        //返回会话id
        return Result.ok(sessionId);
    }

    //保存会话到map列表
    private void saveSessionToMap(Integer serverId, ClientSession session, String sessionId, User user,String userIp) {
        ChannelShell toBackChannel;
        //创建shell通道->后台
        try {
            toBackChannel = session.createShellChannel();
        } catch (IOException e) {
            log.error("建立shell通道失败");
            throw new RuntimeException(e);
        }

        //创建字符缓存区
        StringBuilder stringBuilder = new StringBuilder(2048);

        //封装TerminalSession
        TerminalSession sessionInfo =TerminalSession.builder()
                .sessionId(sessionId)
                .userId(user.getId())
                .serverId(serverId)
                .clientSession(session)
                .clientChannel(toBackChannel)
                .commandBuffer(stringBuilder)
                .userIp(userIp)
                .build();

        //将会话存入map
        ONLINE_SESSION.put(sessionId,sessionInfo);
        log.info("添加会话{}到map-成功",sessionId);
    }

    //创建会话
    private ClientSession createSession(SysRole proxyRole, BackendServer server) {
        ClientSession session;
        try {
            //创建新会话
            session = proxyClient.connect(
                            proxyRole.getRoleCode(),
                            server.getHost(),
                            server.getPort()
                    ).verify(5000)
                    .getSession();

            //进行密码校验
            session.addPasswordIdentity(proxyRole.getPassword());
            session.auth().verify(5000);

            log.info("第二层校验-开启并自动连接终端成功");

        } catch (IOException e) {
            log.error("第二层校验-开启并自动连接终端失败");
            throw new RuntimeException(e);
        }
        return session;
    }

}
