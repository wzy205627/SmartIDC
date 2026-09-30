package com.smartidc.iot.client;

import com.smartidc.iot.config.MqttProperties;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * EMQX MQTT 长连接客户端核心服务
 * 负责客户端生命周期治理：初始化、连接握手、通配订阅、报文发布与优雅断开
 */
@Service
public class EmqxMqttClientService implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EmqxMqttClientService.class);

    private final MqttProperties mqttProperties;
    private final EmqxMqttCallback emqxMqttCallback;

    private MqttClient client;

    public EmqxMqttClientService(MqttProperties mqttProperties, EmqxMqttCallback emqxMqttCallback) {
        this.mqttProperties = mqttProperties;
        this.emqxMqttCallback = emqxMqttCallback;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!mqttProperties.isEnabled()) {
            log.info("[EMQX-MQTT] MQTT 物联接入服务已通过配置禁用 (smartidc.mqtt.enabled=false)");
            return;
        }
        initAndConnect();
    }

    /**
     * 初始化客户端并连接 EMQX Broker
     */
    public synchronized void initAndConnect() {
        String clientId = mqttProperties.getClientIdPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            log.info("[EMQX-MQTT] 正在初始化 MQTT 客户端: host={}, clientId={}", mqttProperties.getHost(), clientId);
            client = new MqttClient(mqttProperties.getHost(), clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setMaxInflight(1000);
            options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
            options.setKeepAliveInterval(mqttProperties.getKeepAlive());
            options.setAutomaticReconnect(mqttProperties.isAutomaticReconnect());

            if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isBlank()) {
                options.setUserName(mqttProperties.getUsername());
            }
            if (mqttProperties.getPassword() != null && !mqttProperties.getPassword().isBlank()) {
                options.setPassword(mqttProperties.getPassword().toCharArray());
            }

            // 绑定回调与断线自动重连后重新订阅
            emqxMqttCallback.setOnReconnectCallback(() -> subscribe(mqttProperties.getTopic(), mqttProperties.getQos()));
            client.setCallback(emqxMqttCallback);

            client.connect(options);
            log.info("[EMQX-MQTT] 成功连接至 EMQX Broker: {}", mqttProperties.getHost());

            // 订阅动环通配主题
            subscribe(mqttProperties.getTopic(), mqttProperties.getQos());
        } catch (MqttException e) {
            log.error("[EMQX-MQTT] 连接 EMQX Broker 失败: host={}, reasonCode={}, error={}",
                    mqttProperties.getHost(), e.getReasonCode(), e.getMessage(), e);
        }
    }

    /**
     * 订阅指定主题
     */
    public void subscribe(String topic, int qos) {
        if (client == null || !client.isConnected()) {
            log.warn("[EMQX-MQTT] 客户端未连接，暂无法订阅主题: {}", topic);
            return;
        }
        try {
            client.subscribe(topic, qos);
            log.info("[EMQX-MQTT] 成功订阅动环主题: topic={}, qos={}", topic, qos);
        } catch (MqttException e) {
            log.error("[EMQX-MQTT] 订阅主题失败: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 发布 MQTT 报文 (供模拟器、单元测试或控制信令下发)
     */
    public void publish(String topic, String payload, int qos) {
        if (client == null || !client.isConnected()) {
            log.warn("[EMQX-MQTT] 客户端未连接，无法发布消息至主题: {}", topic);
            return;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            client.publish(topic, message);
            log.debug("[EMQX-MQTT] 报文发送成功: topic={}, qos={}", topic, qos);
        } catch (MqttException e) {
            log.error("[EMQX-MQTT] 报文发送失败: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 客户端是否处于在线连接状态
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @Override
    public void destroy() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
                log.info("[EMQX-MQTT] MQTT 客户端已安全断开并释放资源");
            } catch (MqttException e) {
                log.warn("[EMQX-MQTT] 客户端释放异常: {}", e.getMessage());
            }
        }
    }
}
