package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.ConnectWay;
import jakarta.servlet.http.HttpServletRequest;

public interface TerminalServerce {
    Result connectToServer(Integer serverId, HttpServletRequest request);
}
