package com.smartidc.iot.buffer;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;

import java.util.List;

/**
 * 动环时序快照持久化处理器 SPI 接口
 * 供下游业务层 (如 smartidc-biz) 接入，实现物联缓冲池与具体数据库的解耦
 */
@FunctionalInterface
public interface TelemetryPersistenceHandler {

    /**
     * 批量持久化处理
     *
     * @param batch 出队的批量指标集合
     */
    void handle(List<TelemetryMetricDTO> batch);
}
