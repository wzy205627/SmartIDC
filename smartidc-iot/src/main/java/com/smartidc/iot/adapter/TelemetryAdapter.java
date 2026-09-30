package com.smartidc.iot.adapter;

import com.smartidc.iot.domain.dto.TelemetryMetricDTO;

/**
 * 动环物联报文防腐层适配器 (ACL)
 * 将来自各厂商异构协议的 MQTT 报文统一适配转化为领域标准指标对象
 */
public interface TelemetryAdapter {

    /**
     * 将原始 JSON/二进制 MQTT 报文转换为标准领域值对象
     *
     * @param topic   MQTT 主题
     * @param payload 原始报文体
     * @return 领域标准遥测指标
     */
    TelemetryMetricDTO convert(String topic, String payload);
}
