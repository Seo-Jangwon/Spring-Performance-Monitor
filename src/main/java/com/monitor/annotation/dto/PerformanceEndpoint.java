/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import java.util.List;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PerformanceEndpoint {

    private String endpointUrl;             // API URL
    private String httpMethod;              // HTTP Method
    private String controllerClassName;     // 컨트롤러 클래스명
    private String controllerMethodName;    // 컨트롤러 메서드명
    private String requestType;             // 요청 파라미터 타입 (클래스명)
    private String responseType;            // 응답 타입 (클래스명)
    private String description;             // 어노테이션 설명
    private List<ServiceMethodInfo> annotatedServices;  // 연관된 서비스 정보들
    private Map<String, String> parameters;             // URL 파라미터 정보
    private Map<String, String> requestExample;         // 요청 예시
}
