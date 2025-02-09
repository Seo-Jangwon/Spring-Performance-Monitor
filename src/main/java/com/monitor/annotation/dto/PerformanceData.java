/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class PerformanceData {

    private String methodName;        // Name of the measured method
    private String description;       // Method description
    private long executionTime;       // Execution time (milliseconds)
    private long memoryUsed;          // Memory used (KB)
    private LocalDateTime timestamp;  // Measurement time
    private String className;         // Class name
    private boolean isSlowExecution;  // Performance bottleneck indicator
    private ThreadMetrics threadMetrics; // Thread metrics

    public static PerformanceData of(String className, String methodName, String description,
        long executionTime, long memoryUsed, ThreadMetrics threadMetrics) {
        return PerformanceData.builder()
            .className(className)
            .methodName(methodName)
            .description(description)
            .executionTime(executionTime)
            .memoryUsed(memoryUsed)
            .timestamp(LocalDateTime.now())
            .isSlowExecution(executionTime > 1000)
            .threadMetrics(threadMetrics)
            .build();
    }
}