/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {
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