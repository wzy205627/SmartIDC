package com.smartidc.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步执行与 JDK 21 虚拟线程 (Virtual Threads) 配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 注册基于 JDK 21 虚拟线程的全局任务执行器
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
