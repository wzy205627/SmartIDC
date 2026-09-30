package com.smartidc.iot.client;

import com.smartidc.iot.adapter.TelemetryAdapter;
import com.smartidc.iot.domain.dto.TelemetryMetricDTO;
import com.smartidc.iot.event.TelemetryEventPublisher;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * EMQX 报文异步监听与连接感知回调
 */
@Component
public class EmqxMqttCallback implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(EmqxMqttCallback.class);

    private final TelemetryAdapter telemetryAdapter;
    private final TelemetryEventPublisher telemetryEventPublisher;
    private Runnable onReconnectCallback;

    public EmqxMqttCallback(TelemetryAdapter telemetryAdapter, TelemetryEventPublisher telemetryEventPublisher) {
        this.telemetryAdapter = telemetryAdapter;
        this.telemetryEventPublisher = telemetryEventPublisher;
    }

    public void setOnReconnectCallback(Runnable onReconnectCallback) {
        this.onReconnectCallback = onReconnectCallback;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("[EMQX-MQTT] 连接建立成功: serverURI={}, 是否重连={}", serverURI, reconnect);
        if (reconnect && onReconnectCallback != null) {
            log.info("[EMQX-MQTT] 断线重连成功，重新订阅主题...");
            try {
                onReconnectCallback.run();
            } catch (Exception e) {
                log.error("[EMQX-MQTT] 重连后订阅失败: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[EMQX-MQTT] MQTT 长连接中断: {}", cause != null ? cause.getMessage() : "未知原因");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.info("[EMQX-MQTT] 收到物联报文: topic={}, qos={}, length={}, payload={}", topic, message.getQos(), payload.length(), payload);

            // 1. 交由 ACL 防腐层标准化处理
            TelemetryMetricDTO metricDTO = telemetryAdapter.convert(topic, payload);

            // 2. 派发至领域事件总线
            if (metricDTO != null) {
                telemetryEventPublisher.publish(metricDTO);
            }
        } catch (Exception e) {
            log.error("[EMQX-MQTT] 报文处理异常. topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 消息发送确认回调 (QoS 1/2)
    }
}
