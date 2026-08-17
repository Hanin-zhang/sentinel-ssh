package com.zhanghan.sshproxyproject.controller;

import com.zhanghan.sshproxyproject.service.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 管理员告警流接口（SSE）
 * <p>
 * 前端 EventSource 连接：{@code /alert/stream?token=xxx}
 * <p>
 * 说明：浏览器原生 EventSource 无法自定义请求头，
 * 一次请求，长久保持连接，服务端主动推数据；
 * 因此 token 通过查询参数传递，由 {@code LoginInterceptor} 兜底读取。
 */
@Tag(name = "告警流", description = "高危告警实时推送（SSE）")
@RestController
@Slf4j
@RequestMapping("/alert")
public class AlertController {

    @Resource
    private SseEmitterRegistry sseEmitterRegistry;

    /**
     * 建立 SSE 连接，实时接收高危告警
     */
    @Operation(summary = "建立 SSE 告警流", description = "管理员订阅后实时接收高危告警，token 通过查询参数传递")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = sseEmitterRegistry.add();
        log.info("管理员告警 SSE 连接建立");
        try {
            // 连接建立后先推一条 connected 事件，前端据此判断连接成功
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (Exception e) {
            log.warn("SSE 连接建立响应发送失败", e);
        }
        return emitter;
    }
}
