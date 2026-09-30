package com.smartidc.biz.listener;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import com.smartidc.iot.event.TelemetryArrivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 动环高频遥测全双工 WebSocket 广播监听器
 * 监听领域事件 TelemetryArrivedEvent，向单机柜频道 /topic/rack-telemetry/{rackCode} 广播秒级指标流
 */
@Component
public class WebSocketTelemetryBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketTelemetryBroadcastListener.class);
    private static final String TELEMETRY_TOPIC_PREFIX = "/topic/rack-telemetry/";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketTelemetryBroadcastListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("rackCode", rackCode.trim());
        payload.put("temperature", metric.getTemperature());
        payload.put("humidity", metric.getHumidity());
        payload.put("voltage", metric.getVoltage());
        payload.put("currentAmp", metric.getCurrentAmp());
        payload.put("powerKw", metric.getPowerKw());
        payload.put("timestamp", metric.getSampleTimestamp() != null ? metric.getSampleTimestamp() : System.currentTimeMillis());

        String destination = TELEMETRY_TOPIC_PREFIX + rackCode.trim();
        try {
            messagingTemplate.convertAndSend(destination, payload);
            if (log.isTraceEnabled()) {
                log.trace("[WebSocket-Telemetry] 广播机柜遥测 -> {}: temp={}, volt={}",
                        destination, metric.getTemperature(), metric.getVoltage());
            }
        } catch (Exception e) {
            log.error("[WebSocket-Telemetry] 广播机柜遥测失败 {}: {}", destination, e.getMessage());
        }
    }
}
