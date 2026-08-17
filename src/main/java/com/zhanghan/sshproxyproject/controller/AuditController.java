package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.PageQueryDTO;
import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.service.IAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "审计日志", description = "命令审计日志查询")
@RestController
@Slf4j
@RequestMapping("/audit")
public class AuditController {

    @Resource
    private IAuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志")
    @GetMapping("/list")
    public Result getAuditLog(PageQueryDTO pageQueryDTO){
        log.info("分页查询");
        return auditLogService.mypage(pageQueryDTO);
    }

    @Operation(summary = "查询审计日志详情")
    @GetMapping("/detail/{id}")
    public Result getAuditDetail(@PathVariable Long id){
        log.info("查询审计日志详情 id={}", id);
        return auditLogService.getDetailById(id);
    }
}
