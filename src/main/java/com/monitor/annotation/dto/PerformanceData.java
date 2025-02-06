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

    private String methodName;        // 측정된 메서드 이름
    private String description;       // 메서드 설명
    private long executionTime;       // 실행 시간 (밀리초)
    private long memoryUsed;          // 사용된 메모리 (KB)
    private LocalDateTime timestamp;  // 측정 시간
    private String className;         // 클래스 이름
    private boolean isSlowExecution;  // 성능 병목 여부
    private ThreadMetrics threadMetrics; // 쓰레드 메트릭

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