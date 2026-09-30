package com.smartidc.iot.event;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 动环领域事件发布网关
 * 负责将防腐后的 TelemetryMetricDTO 派发至 Spring 事件总线
 */
@Component
public class TelemetryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryEventPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public TelemetryEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布遥测到达事件
     *
     * @param metric 标准指标 DTO
     */
    public void publish(TelemetryMetricDTO metric) {
        if (metric == null) {
            return;
        }
        log.info("[TelemetryEventBus] 派发动环遥测事件: 机柜=[{}], 温度=[{}℃], 湿度=[{}%], 电压=[{}V], 功率=[{}kW]",
                metric.getRackCode(), metric.getTemperature(), metric.getHumidity(), metric.getVoltage(), metric.getPowerKw());
        eventPublisher.publishEvent(new TelemetryArrivedEvent(this, metric));
    }
}
