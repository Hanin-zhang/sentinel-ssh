package com.zhanghan.sshproxyproject.service;

import com.zhanghan.sshproxyproject.dto.Result;

public interface IDashboardService {
    Result getData();

    Result getServers();
}
