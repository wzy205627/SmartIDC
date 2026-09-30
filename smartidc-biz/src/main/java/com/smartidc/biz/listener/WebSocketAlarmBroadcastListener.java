package com.smartidc.biz.listener;

import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import com.smartidc.biz.domain.entity.IdcRack;
import com.smartidc.biz.domain.event.AlarmClearedEvent;
import com.smartidc.biz.domain.event.AlarmTriggeredEvent;
import com.smartidc.biz.mapper.IdcRackMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动环告警全双工 WebSocket 广播监听器
 * 监听领域事件 AlarmTriggeredEvent / AlarmClearedEvent，实时向前端 /topic/alarms 广播 JSON 报文
 */
@Component
public class WebSocketAlarmBroadcastListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAlarmBroadcastListener.class);
    private static final String ALARMS_TOPIC = "/topic/alarms";

    private final SimpMessagingTemplate messagingTemplate;
    private final IdcRackMapper rackMapper;

    /**
     * 机柜 rackId -> rackCode 本地快取
     */
    private final Map<Long, String> rackCodeCache = new ConcurrentHashMap<>();

    public WebSocketAlarmBroadcastListener(SimpMessagingTemplate messagingTemplate,
                                           IdcRackMapper rackMapper) {
        this.messagingTemplate = messagingTemplate;
        this.rackMapper = rackMapper;
    }

    @EventListener
    public void onAlarmTriggered(AlarmTriggeredEvent event) {
        if (event == null || event.getAlarmEvent() == null) {
            return;
        }

        IdcAlarmEvent alarm = event.getAlarmEvent();
        String rackCode = event.getRackCode();
        if ((rackCode == null || rackCode.isBlank()) && alarm.getRackId() != null) {
            rackCode = getRackCodeById(alarm.getRackId());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "TRIGGERED");
        payload.put("alarmId", alarm.getAlarmId());
        payload.put("rackId", alarm.getRackId());
        payload.put("rackCode", rackCode != null ? rackCode : "UNKNOWN");
        payload.put("roomName", event.getRoomName() != null ? event.getRoomName() : "默认机房");
        payload.put("alarmLevel", alarm.getAlarmLevel());
        payload.put("alarmType", alarm.getAlarmType());
        payload.put("metricValue", alarm.getMetricValue());
        payload.put("rcaSummary", alarm.getRcaSummary());
        payload.put("triggerTime", alarm.getTriggerTime() != null ? alarm.getTriggerTime().toString() : LocalDateTime.now().toString());
        payload.put("timestamp", System.currentTimeMillis());

        try {
            messagingTemplate.convertAndSend(ALARMS_TOPIC, payload);
            log.info("[WebSocket-Alarm] 广播告警触发通知 -> topic: {}, alarmId: {}, rack: {}, level: {}",
                    ALARMS_TOPIC, alarm.getAlarmId(), rackCode, alarm.getAlarmLevel());
        } catch (Exception e) {
            log.error("[WebSocket-Alarm] 广播告警通知失败: {}", e.getMessage(), e);
        }
    }

    @EventListener
    public void onAlarmCleared(AlarmClearedEvent event) {
        if (event == null || event.getAlarmEvent() == null) {
            return;
        }

        IdcAlarmEvent alarm = event.getAlarmEvent();
        String rackCode = event.getRackCode();
        if ((rackCode == null || rackCode.matches("^\\d+$")) && alarm.getRackId() != null) {
            String resolved = getRackCodeById(alarm.getRackId());
            if (resolved != null) {
                rackCode = resolved;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "CLEARED");
        payload.put("alarmId", alarm.getAlarmId());
        payload.put("rackId", alarm.getRackId());
        payload.put("rackCode", rackCode != null ? rackCode : "UNKNOWN");
        payload.put("alarmLevel", alarm.getAlarmLevel());
        payload.put("alarmType", alarm.getAlarmType());
        payload.put("clearTime", alarm.getClearTime() != null ? alarm.getClearTime().toString() : LocalDateTime.now().toString());
        payload.put("timestamp", System.currentTimeMillis());

        try {
            messagingTemplate.convertAndSend(ALARMS_TOPIC, payload);
            log.info("[WebSocket-Alarm] 广播告警消除通知 -> topic: {}, alarmId: {}, rack: {}",
                    ALARMS_TOPIC, alarm.getAlarmId(), rackCode);
        } catch (Exception e) {
            log.error("[WebSocket-Alarm] 广播消警通知失败: {}", e.getMessage(), e);
        }
    }

    private String getRackCodeById(Long rackId) {
        if (rackId == null) {
            return null;
        }
        return rackCodeCache.computeIfAbsent(rackId, id -> {
            IdcRack rack = rackMapper.selectById(id);
            return rack != null ? rack.getRackCode() : null;
        });
    }
}
