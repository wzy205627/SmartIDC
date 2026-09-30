package com.smartidc.iot.event;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.springframework.context.ApplicationEvent;

/**
 * 动环遥测指标到达领域事件
 * 当 MQTT 接收到报文并经由 ACL 防腐层转化为标准 DTO 后触发
 */
public class TelemetryArrivedEvent extends ApplicationEvent {

    private final TelemetryMetricDTO metric;

    public TelemetryArrivedEvent(Object source, TelemetryMetricDTO metric) {
        super(source);
        this.metric = metric;
    }

    public TelemetryMetricDTO getMetric() {
        return metric;
    }
}
