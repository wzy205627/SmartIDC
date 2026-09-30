package com.smartidc.iot.client;

import org.springframework.stereotype.Component;

/**
 * MQTT 报文发布辅助工具
 * 提供语义化方法向指定租户与机柜发布遥测报文 (供模拟器与测试调用)
 */
@Component
public class MqttMessageSender {

    private final EmqxMqttClientService clientService;

    public MqttMessageSender(EmqxMqttClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * 向指定租户和机柜发布标准动环遥测报文
     *
     * @param tenantId    租户ID (如: 000000)
     * @param rackCode    机柜编号 (如: A-03)
     * @param jsonPayload 报文 JSON 字符串
     */
    public void sendTelemetry(String tenantId, String rackCode, String jsonPayload) {
        String topic = String.format("/sys/smartidc/%s/rack/%s/telemetry", tenantId, rackCode);
        clientService.publish(topic, jsonPayload, 1);
    }

    /**
     * 直接向指定主题发送报文
     *
     * @param topic   目标主题
     * @param payload 报文内容
     * @param qos     服务质量 (0, 1, 2)
     */
    public void sendRaw(String topic, String payload, int qos) {
        clientService.publish(topic, payload, qos);
    }
}
