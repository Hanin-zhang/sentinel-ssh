package com.zhanghan.sshproxyproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhanghan.sshproxyproject.dto.PageQueryDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.entity.AuditLog;

public interface IAuditLogService extends IService<AuditLog> {
    Result mypage(PageQueryDTO pageQueryDTO);

    Result getDetailById(Long id);
}
