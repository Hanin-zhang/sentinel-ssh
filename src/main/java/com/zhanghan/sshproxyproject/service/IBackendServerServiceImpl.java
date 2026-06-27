package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhanghan.sshproxyproject.entity.AuditLog;
import com.zhanghan.sshproxyproject.entity.BackendServer;
import com.zhanghan.sshproxyproject.mapper.AuditLogMapper;
import com.zhanghan.sshproxyproject.mapper.BackendServerMapper;
import org.springframework.stereotype.Service;

@Service
public class IBackendServerServiceImpl extends ServiceImpl<BackendServerMapper, BackendServer> implements IBackendServerService  {

}
