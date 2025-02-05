/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.model;

import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class TestScenarioRequest {

    private String url;                     // 테스트할 API 엔드포인트
    private String method;                  // HTTP 메서드 (GET, POST 등)
    private String requestBody;             // 요청 본문 (JSON 문자열)
    private Map<String, String> headers;    // HTTP 헤더들
    private int concurrentUsers;            // 동시 사용자 수
    private int repeatCount;                // 반복 횟수
    private int rampUpSeconds;              // 부하 증가 시간(초)
    private String description;             // 테스트 설명
    private int timeoutSeconds = 60;        // 기본 타임아웃 60초
}