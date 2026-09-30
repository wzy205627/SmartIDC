package com.smartidc.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 基础安全链配置
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 放行 Knife4j 接口文档静态资源与接口定义
                        .requestMatchers(
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/favicon.ico"
                        ).permitAll()
                        // 开放业务测试接口 (Phase 0 基准联调)
                        .requestMatchers("/api/v1/**").permitAll()
                        // 开放 WebSocket STOMP 握手与消息通道
                        .requestMatchers("/ws/**", "/ws/smartidc/**").permitAll()
                        // 其余请求需要认证
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
