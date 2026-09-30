package com.smartidc.iot.listener;

import com.smartidc.iot.buffer.TelemetryBatchBuffer;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import com.smartidc.iot.event.TelemetryArrivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 动环领域事件监听器 (双轨分流核心节点)
 * 职责：
 * 1. 监听 TelemetryArrivedEvent；
 * 2. 【热数据轨】：写入 Redis Hash (smartidc:telemetry:latest:{rackCode})，TTL 300s；
 * 3. 【温数据轨】：压入并发阻塞缓冲池 TelemetryBatchBuffer，等待攒批削峰入库。
 */
@Component
public class TelemetryEventListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventListener.class);

    private static final String REDIS_KEY_PREFIX = "smartidc:telemetry:latest:";
    private static final long REDIS_TTL_SECONDS = 300L;

    private final StringRedisTemplate redisTemplate;
    private final TelemetryBatchBuffer batchBuffer;

    public TelemetryEventListener(StringRedisTemplate redisTemplate, TelemetryBatchBuffer batchBuffer) {
        this.redisTemplate = redisTemplate;
        this.batchBuffer = batchBuffer;
    }

    @EventListener
    public void onTelemetryArrived(TelemetryArrivedEvent event) {
        if (event == null || event.getMetric() == null) {
            return;
        }

        TelemetryMetricDTO metric = event.getMetric();
        String rackCode = metric.getRackCode();
        if (rackCode == null || rackCode.isBlank()) {
            return;
        }

        // 1. 【热数据轨】写入 Redis Hash
        try {
            writeHotCache(metric);
        } catch (Exception e) {
            log.warn("[TelemetryEventListener] Redis 热缓存写入失败: rackCode={}, error={}", rackCode, e.getMessage());
        }

        // 2. 【温时序轨】压入批量缓冲池 (零阻塞)
        batchBuffer.offer(metric);
    }

    /**
     * 将最新指标写入 Redis Hash
     */
    private void writeHotCache(TelemetryMetricDTO metric) {
        String key = REDIS_KEY_PREFIX + metric.getRackCode().trim();
        Map<String, String> map = new HashMap<>();

        if (metric.getTemperature() != null) {
            map.put("temperature", metric.getTemperature().toPlainString());
        }
        if (metric.getHumidity() != null) {
            map.put("humidity", metric.getHumidity().toPlainString());
        }
        if (metric.getVoltage() != null) {
            map.put("voltage", metric.getVoltage().toPlainString());
        }
        if (metric.getCurrentAmp() != null) {
            map.put("currentAmp", metric.getCurrentAmp().toPlainString());
        }
        if (metric.getPowerKw() != null) {
            map.put("powerKw", metric.getPowerKw().toPlainString());
        }
        if (metric.getDeviceKey() != null) {
            map.put("deviceKey", metric.getDeviceKey());
        }

        long sampleTs = metric.getSampleTimestamp() != null && metric.getSampleTimestamp() > 0
                ? metric.getSampleTimestamp()
                : System.currentTimeMillis();
        map.put("sampleTime", String.valueOf(sampleTs));
        map.put("updateTime", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(key, map);
        redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        log.debug("[TelemetryEventListener] 成功更新 Redis 热缓存: key={}", key);
    }
}
