package com.smartidc.biz.domain.event;

import com.smartidc.biz.domain.entity.IdcAlarmEvent;
import org.springframework.context.ApplicationEvent;

/**
 * 动环告警自动消除事件
 */
public class AlarmClearedEvent extends ApplicationEvent {

    private final IdcAlarmEvent alarmEvent;
    private final String rackCode;

    public AlarmClearedEvent(Object source, IdcAlarmEvent alarmEvent, String rackCode) {
        super(source);
        this.alarmEvent = alarmEvent;
        this.rackCode = rackCode;
    }

    public IdcAlarmEvent getAlarmEvent() {
        return alarmEvent;
    }

    public String getRackCode() {
        return rackCode;
    }
}
