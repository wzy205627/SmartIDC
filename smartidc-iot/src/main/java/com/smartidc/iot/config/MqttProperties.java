package com.smartidc.iot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EMQX 物联网 MQTT 连接属性配置类
 */
@Component
@ConfigurationProperties(prefix = "smartidc.mqtt")
public class MqttProperties {

    /**
     * 是否启用 MQTT 物联接入
     */
    private boolean enabled = true;

    /**
     * MQTT Broker 服务端地址 (如 tcp://localhost:1883)
     */
    private String host = "tcp://localhost:1883";

    /**
     * 客户端 ClientId 前缀
     */
    private String clientIdPrefix = "smartidc-iot-service";

    /**
     * 认证用户名 (可选)
     */
    private String username = "";

    /**
     * 认证密码 (可选)
     */
    private String password = "";

    /**
     * 默认订阅通配主题
     */
    private String topic = "/sys/smartidc/+/rack/+/telemetry";

    /**
     * 消息服务质量等级 (QoS 0, 1, 2)
     */
    private int qos = 1;

    /**
     * 心跳维持间隔 (秒)
     */
    private int keepAlive = 60;

    /**
     * 连接超时时间 (秒)
     */
    private int connectionTimeout = 10;

    /**
     * 是否启用断线自动重连
     */
    private boolean automaticReconnect = true;

    public MqttProperties() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getClientIdPrefix() {
        return clientIdPrefix;
    }

    public void setClientIdPrefix(String clientIdPrefix) {
        this.clientIdPrefix = clientIdPrefix;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(int keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }
}
