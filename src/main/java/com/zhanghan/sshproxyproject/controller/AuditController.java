package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.PageQueryDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.service.IAuditLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/audit")
public class AuditController {

    @Resource
    private IAuditLogService auditLogService;

    @GetMapping("/list")
    public Result getAuditLog(PageQueryDTO pageQueryDTO){
        log.info("分页查询");
        return auditLogService.mypage(pageQueryDTO);
    }

    @GetMapping("/detail/{id}")
    public Result getAuditDetail(@PathVariable Long id){
        log.info("查询审计日志详情 id={}", id);
        return auditLogService.getDetailById(id);
    }
}
