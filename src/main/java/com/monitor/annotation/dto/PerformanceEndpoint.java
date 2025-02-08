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
    private String controllerClassName;     // Controller class name
    private String controllerMethodName;    // Controller method name
    private String requestType;             // Request parameter type (class name)
    private String responseType;            // Response type (class name)
    private String description;             // Annotation description
    private List<ServiceMethodInfo> annotatedServices;  // Associated service information
    private Map<String, String> parameters;             // URL parameter information
    private Map<String, Object> requestExample;         // Request example
}
