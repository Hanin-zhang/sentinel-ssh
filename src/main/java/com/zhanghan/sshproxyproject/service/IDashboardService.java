package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.dto.Result;

public interface IDashboardService {
    Result getData();

    Result getServers();

    /*
    * 获取在线用户列表
    * */
    Result getOnlineUsers();
}
