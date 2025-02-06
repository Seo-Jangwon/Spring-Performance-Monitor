/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.controller;

import com.monitor.annotation.dto.PerformanceEndpoint;
import com.monitor.annotation.dto.TestResult;
import com.monitor.annotation.dto.TestScenarioRequest;
import com.monitor.annotation.scanner.PerformanceEndpointScanner;
import com.monitor.annotation.service.PerformanceTestService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/performanceMeasure")
@RequiredArgsConstructor
public class PerformanceTestController {

    private final PerformanceTestService performanceTestService;
    private final PerformanceEndpointScanner scanner;

    @GetMapping
    public String showTestPage(Model model) {
        Map<String, List<PerformanceEndpoint>> endpoints = scanner.scanEndpoints();
        log.info("Found endpoints for UI: {}", endpoints);
        model.addAttribute("testScenarios", performanceTestService.getAllTestResults());
        model.addAttribute("endpoints", endpoints);
        return "performance-test";
    }

    @GetMapping("/endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, List<PerformanceEndpoint>>> getEndpoints() {
        Map<String, List<PerformanceEndpoint>> endpoints = scanner.scanEndpoints();
        return ResponseEntity.ok(endpoints);
    }

    @PostMapping("/run")
    @ResponseBody
    public String runTest(@RequestBody TestScenarioRequest request) {
        log.info("Received test request: {}", request);
        String testId = performanceTestService.startNewTest(request);
        log.info("Generated test ID: {}", testId);
        return testId;
    }

    @GetMapping("/status/{testId}")
    @ResponseBody
    public Object getTestStatus(@PathVariable String testId) {
        return performanceTestService.getTestStatus(testId);
    }

    @GetMapping("/results")
    @ResponseBody
    public List<TestResult> getResults() {
        return performanceTestService.getAllTestResults();
    }
}