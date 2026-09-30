package com.smartidc.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring WebSocket STOMP 消息代理与全双工端点配置
 * 提供前端实时告警推屏 (/topic/alarms) 与单机柜高频遥测流 (/topic/rack-telemetry/{rackCode})
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用内存中内置的 SimpleBroker，客户端订阅 /topic 开头的广播通道
        config.enableSimpleBroker("/topic");
        // 客户端向服务端发起业务调用时的前缀 (保留扩展)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册标准 STOMP 全双工端点，支持跨域访问
        registry.addEndpoint("/ws/smartidc")
                .setAllowedOriginPatterns("*");

        // 注册兼容 SockJS 的降级端点
        registry.addEndpoint("/ws/smartidc")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
