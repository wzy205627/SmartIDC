package com.smartidc.iot.buffer;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动环批量削峰写入调度器
 * 核心调度逻辑：
 * 1. 周期性轮询 (fixedDelay = 500ms)；
 * 2. 检测双触发条件：满 100 条 或 距上次写入满 2000ms；
 * 3. 批量出队交付 TelemetryPersistenceHandler 执行批处理落盘；
 * 4. 容器停止时执行优雅 Flush，杜绝内存数据丢失。
 */
@Component
public class TelemetryBatchFlusher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBatchFlusher.class);

    private static final int BATCH_SIZE_THRESHOLD = 100;
    private static final long TIME_THRESHOLD_MS = 2000L;
    private static final int MAX_DRAIN_LIMIT = 500;

    private final TelemetryBatchBuffer batchBuffer;
    private final List<TelemetryPersistenceHandler> persistenceHandlers;

    public TelemetryBatchFlusher(TelemetryBatchBuffer batchBuffer,
                                 @Autowired(required = false) List<TelemetryPersistenceHandler> persistenceHandlers) {
        this.batchBuffer = batchBuffer;
        this.persistenceHandlers = persistenceHandlers != null ? persistenceHandlers : List.of();
    }

    /**
     * 定时轮询检测与批量刷盘 (每 500ms 巡检一次)
     */
    @Scheduled(fixedDelay = 500)
    public void scheduleFlush() {
        if (batchBuffer.shouldFlush(BATCH_SIZE_THRESHOLD, TIME_THRESHOLD_MS)) {
            flushBatch();
        }
    }

    /**
     * 执行单次批量提取与持久化
     */
    public synchronized void flushBatch() {
        List<TelemetryMetricDTO> batch = batchBuffer.drain(MAX_DRAIN_LIMIT);
        if (batch == null || batch.isEmpty()) {
            return;
        }

        if (persistenceHandlers.isEmpty()) {
            log.warn("[TelemetryFlusher] 未检测到任何 TelemetryPersistenceHandler 实现类，丢弃批次数据: count={}", batch.size());
            return;
        }

        for (TelemetryPersistenceHandler handler : persistenceHandlers) {
            try {
                handler.handle(batch);
            } catch (Exception e) {
                log.error("[TelemetryFlusher] 批处理持久化异常: handler={}, error={}",
                        handler.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public void destroy() {
        log.info("[TelemetryFlusher] 容器关闭，正在执行最终批处理持久化 (剩余待刷盘条数={})...", batchBuffer.size());
        while (batchBuffer.size() > 0) {
            flushBatch();
        }
    }
}
