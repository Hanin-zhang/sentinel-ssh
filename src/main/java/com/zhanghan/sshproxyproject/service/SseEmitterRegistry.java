package com.zhanghan.sshproxyproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 连接池 — 管理所有管理员告警流的连接
 * <p>
 * 由 {@code AlertController} 在连接建立时调用 {@link #add()} 注册，
 * 由 {@code AlertService} 在高危告警时调用 {@link #broadcast(String)} 推送。
 * <p>
 * 使用 {@code CopyOnWriteArrayList} 保证并发读写安全，
 * 连接完成后通过回调自动从池中移除。
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 注册一个新的 SSE 连接
     *
     * Sse 服务端单向推消息给浏览器，浏览器不发请求
     * @return 新建的 SseEmitter，交给 Controller 返回给客户端
     */
    public SseEmitter add() {
        // timeout=0 表示永不超时，连接由客户端或心跳清理
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * 向所有管理员连接广播一条高危告警（JSON 文本）
     */
    public void broadcast(String data) {
        if (emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("alert").data(data));
            } catch (Exception e) {
                log.warn("SSE 推送失败，移除失效连接", e);
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 心跳：每 15s 向所有连接发送注释帧，防止连接被空闲关闭，同时清理失效连接
     */
    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
