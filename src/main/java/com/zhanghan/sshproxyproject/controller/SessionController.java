package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.dto.Result;
import com.zhanghan.sshproxyproject.session.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理接口 — 管理员踢人
 */
@Tag(name = "会话管理", description = "在线会话管理与管理员踢人")
@RestController
@Slf4j
@RequestMapping("/session")
public class SessionController {

    @Resource
    private SessionManager sessionManager;

    /**
     * 管理员强制断开指定会话
     *
     * @param sessionId 在线会话 ID（见 /dashboard/online-users 返回的 sessionId）
     */
    @Operation(summary = "踢人", description = "管理员强制断开指定在线会话")
    @PostMapping("/kick/{sessionId}")
    public Result kick(@PathVariable String sessionId) {
        log.warn("管理员踢人: sessionId={}", sessionId);
        boolean ok = sessionManager.forceDisconnect(sessionId);
        return ok ? Result.ok() : Result.fail("会话不存在或已断开");
    }
}
