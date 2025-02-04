/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TestResult {

    private String testId;
    private String description;
    private String url;
    private String method;
    private boolean completed;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int totalRequests;
    private int successfulRequests;
    private int failedRequests;
    private double averageResponseTime;
    private double maxResponseTime;
    private double minResponseTime;
    private double requestsPerSecond;
    private double errorRate;
    private List<Long> responseTimes;  // 모든 요청의 응답 시간 목록
    private String status;           // 테스트 상태 (COMPLETED, ERROR, TIMEOUT 등)
    private String errorMessage;    // 에러 메시지
}