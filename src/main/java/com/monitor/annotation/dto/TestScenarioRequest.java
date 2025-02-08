/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class TestScenarioRequest {

    private String url;                     // API endpoint to be tested
    private String method;                  // HTTP method (GET, POST, etc.)
    private String requestBody;             // Request body (JSON string)
    private Map<String, String> headers;    // HTTP headers
    private int concurrentUsers;            // Number of concurrent users
    private int repeatCount;                // Number of repetitions
    private int rampUpSeconds;              // Load ramp-up time (seconds)
    private String description;             // Test description
    private int timeoutSeconds = 60;        // Default timeout of 60 seconds
}