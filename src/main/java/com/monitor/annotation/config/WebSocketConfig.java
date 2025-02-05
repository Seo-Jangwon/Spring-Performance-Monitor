/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.config;

import com.monitor.annotation.handler.MetricsWebSocketHandler;
import com.monitor.annotation.service.MemoryMonitorService;
import com.monitor.annotation.service.PerformanceTestService;
import com.monitor.annotation.service.ThreadMonitorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MemoryMonitorService memoryMonitorService;
    private final PerformanceTestService performanceTestService;
    private final ThreadMonitorService threadMonitorService;

    public WebSocketConfig(MemoryMonitorService memoryMonitorService,
        PerformanceTestService performanceTestService, ThreadMonitorService threadMonitorService) {
        this.memoryMonitorService = memoryMonitorService;
        this.performanceTestService = performanceTestService;
        this.threadMonitorService = threadMonitorService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(metricsWebSocketHandler(), "/ws/metrics")
            .setAllowedOrigins("*");
    }

    @Bean
    public MetricsWebSocketHandler metricsWebSocketHandler() {
        return new MetricsWebSocketHandler(memoryMonitorService, performanceTestService, threadMonitorService);
    }
}