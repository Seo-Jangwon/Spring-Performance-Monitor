/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class ServiceMethodInfo {

    private String serviceClassName;  // 서비스 클래스명
    private String methodName;        // 메서드명
    private String description;       // 어노테이션에 달린 설명
}