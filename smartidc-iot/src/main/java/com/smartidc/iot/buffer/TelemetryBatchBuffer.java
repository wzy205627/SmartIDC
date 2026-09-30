package com.smartidc.iot.buffer;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 动环高并发批处理并发缓冲池 (削峰池)
 * 职责：
 * 1. 采用并发安全有界阻塞队列 (默认 10,000 条上限) 隔离写入压力，防止 OOM；
 * 2. 提供双触发决策 (满 100 条 或 间隔 2000ms)；
 * 3. 线程安全批量出队。
 */
@Component
public class TelemetryBatchBuffer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBatchBuffer.class);

    /**
     * 队列最大容量上限 (防 OOM 兜底)
     */
    public static final int DEFAULT_MAX_CAPACITY = 10000;

    private final BlockingQueue<TelemetryMetricDTO> queue;
    private final AtomicLong lastFlushTimestamp;

    public TelemetryBatchBuffer() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public TelemetryBatchBuffer(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.lastFlushTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * 将防腐清洗后的标准指标压入缓冲池
     *
     * @param metric 标准指标 DTO
     * @return 是否成功入队
     */
    public boolean offer(TelemetryMetricDTO metric) {
        if (metric == null) {
            return false;
        }

        boolean success = queue.offer(metric);
        if (!success) {
            log.error("[TelemetryBatchBuffer] 缓冲池达到容量上限[{}], 触发防 OOM 丢弃拦截! 机柜=[{}], 时间戳=[{}]",
                    queue.size(), metric.getRackCode(), metric.getSampleTimestamp());
            return false;
        }
        return true;
    }

    /**
     * 判断当前是否满足批处理刷新触发条件
     * 1. 积压量达到 countThreshold (默认 100 条)
     * 2. 距离上次刷新超过 timeThresholdMs (默认 2000 ms) 且队列非空
     *
     * @param countThreshold  批量数量阈值
     * @param timeThresholdMs 最大等待超时毫秒数
     * @return 是否应该立即 flush
     */
    public boolean shouldFlush(int countThreshold, long timeThresholdMs) {
        int currentSize = queue.size();
        if (currentSize == 0) {
            return false;
        }

        if (currentSize >= countThreshold) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastFlushTimestamp.get();
        return elapsed >= timeThresholdMs;
    }

    /**
     * 批量排干出队
     *
     * @param maxBatchSize 单次最大出队数量 (如 500)
     * @return 抽取的批次集合
     */
    public List<TelemetryMetricDTO> drain(int maxBatchSize) {
        List<TelemetryMetricDTO> batch = new ArrayList<>(Math.min(queue.size(), maxBatchSize));
        queue.drainTo(batch, maxBatchSize);
        lastFlushTimestamp.set(System.currentTimeMillis());
        return batch;
    }

    /**
     * 当前缓冲池积压条数
     */
    public int size() {
        return queue.size();
    }

    /**
     * 清空缓冲池 (供测试及重置使用)
     */
    public void clear() {
        queue.clear();
        lastFlushTimestamp.set(System.currentTimeMillis());
    }
}
