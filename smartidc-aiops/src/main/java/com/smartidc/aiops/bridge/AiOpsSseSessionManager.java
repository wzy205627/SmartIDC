package com.smartidc.aiops.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接生命周期与心跳保活管理器 (对标 implementation_plan4.5.md 任务 2)
 * 工业级防御设计：
 * 1. ConcurrentHashMap 纳管活跃连接
 * 2. 监听 completion/timeout/error 防内存泄漏
 * 3. 15 秒定时心跳 ping 注释帧保活，防止 Nginx / 浏览器 60 秒超时断开
 * 4. 客户端意外断开容错防崩溃，仅注销连接，绝不中断后台图执行与 Redis 快照落库
 */
@Component
public class AiOpsSseSessionManager {

    private static final Logger log = LoggerFactory.getLogger(AiOpsSseSessionManager.class);

    private final Map<String, SseEmitter> sessionMap = new ConcurrentHashMap<>();

    /**
     * 创建并注册 SSE 会话
     *
     * @param traceId       链路追踪标识
     * @param timeoutMillis 超时毫秒数 (默认建议 600000 即 10 分钟)
     * @return SseEmitter 实例
     */
    public SseEmitter createSession(String traceId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        sessionMap.put(traceId, emitter);

        emitter.onCompletion(() -> {
            log.info("🔌 [SSE Session] 正常完成并关闭, traceId: {}", traceId);
            sessionMap.remove(traceId);
        });

        emitter.onTimeout(() -> {
            log.warn("⏰ [SSE Session] 连接超时自动释放, traceId: {}", traceId);
            sessionMap.remove(traceId);
        });

        emitter.onError(e -> {
            log.warn("⚠️ [SSE Session] 客户端异常中断 (如刷新浏览器), traceId: {}, error: {}", traceId, e.getMessage());
            sessionMap.remove(traceId);
        });

        return emitter;
    }

    /**
     * 向指定会话推送具名事件
     *
     * @param traceId   链路追踪标识
     * @param eventName 事件名称 (如 thinking / tool / hitl_interrupt / completed / error)
     * @param data      负载数据
     */
    public void sendEvent(String traceId, String eventName, Object data) {
        SseEmitter emitter = sessionMap.get(traceId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("⚠️ 向客户端推送 SSE 事件失败 (客户端可能已断开), traceId: {}, event: {}", traceId, eventName);
            sessionMap.remove(traceId);
        } catch (Exception e) {
            log.warn("⚠️ 推送 SSE 事件未知异常, traceId: {}, error: {}", traceId, e.getMessage());
            sessionMap.remove(traceId);
        }
    }

    /**
     * 15 秒定时扫描活跃会话并发送 ping 心跳保活帧
     */
    @Scheduled(fixedDelay = 15000)
    public void sendHeartbeat() {
        if (sessionMap.isEmpty()) {
            return;
        }

        sessionMap.forEach((traceId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.debug("🔌 心跳检测发现失效连接，自动移除: {}", traceId);
                sessionMap.remove(traceId);
            }
        });
    }

    /**
     * 安全关闭并注销 SSE 会话
     *
     * @param traceId 链路追踪标识
     */
    public void closeSession(String traceId) {
        SseEmitter emitter = sessionMap.remove(traceId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 获取当前活跃会话数
     */
    public int getActiveSessionCount() {
        return sessionMap.size();
    }
}
