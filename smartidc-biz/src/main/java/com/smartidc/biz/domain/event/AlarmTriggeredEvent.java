package com.smartidc.biz.domain.event;

import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import org.springframework.context.ApplicationEvent;

/**
 * 动环告警触发事件 (供 WebSocket 推屏广播与 AIOps 智能工单消费)
 */
public class AlarmTriggeredEvent extends ApplicationEvent {

    private final IdcAlarmEvent alarmEvent;
    private final String rackCode;
    private final String roomName;

    public AlarmTriggeredEvent(Object source, IdcAlarmEvent alarmEvent, String rackCode, String roomName) {
        super(source);
        this.alarmEvent = alarmEvent;
        this.rackCode = rackCode;
        this.roomName = roomName;
    }

    public IdcAlarmEvent getAlarmEvent() {
        return alarmEvent;
    }

    public String getRackCode() {
        return rackCode;
    }

    public String getRoomName() {
        return roomName;
    }
}
