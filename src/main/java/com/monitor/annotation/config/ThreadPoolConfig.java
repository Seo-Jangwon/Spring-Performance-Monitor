/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration class for thread pool executors.
 * Defines two separate thread pools:
 * - Performance test executor - for handling performance test requests
 * - Monitor thread executor - for handling monitoring tasks
 */
@Configuration
public class ThreadPoolConfig {
    /**
     * Creates thread pool for performance testing with following settings
     * - Core pool size: 10
     * - Max pool size: 50
     * - Queue capacity: 100
     * - Thread name prefix: "PerfTest-"
     */
    @Bean("performanceTestExecutor")
    public ThreadPoolTaskExecutor performanceTestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("PerfTest-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Creates thread pool for monitoring tasks with following settings
     * - Core pool size: 10
     * - Max pool size: 50
     * - Queue capacity: 100
     * - Thread name prefix: "MonitorThread-"
     */
    @Bean("monitorThreadExecutor")
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("MonitorThread-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}