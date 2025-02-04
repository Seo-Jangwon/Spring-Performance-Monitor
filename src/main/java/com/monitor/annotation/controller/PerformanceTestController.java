/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.controller;

import com.monitor.annotation.model.TestResult;
import com.monitor.annotation.model.TestScenarioRequest;
import com.monitor.annotation.service.PerformanceTestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/performanceMeasure")
@RequiredArgsConstructor
public class PerformanceTestController {
    private final PerformanceTestService performanceTestService;

    @GetMapping
    public String showTestPage(Model model) {
        model.addAttribute("testScenarios", performanceTestService.getAllTestResults());
        return "performance-test";
    }

    @PostMapping("/run")
    @ResponseBody
    public String runTest(@RequestBody TestScenarioRequest request) {
        log.info("Received test request: {}", request);  // 로그 추가
        String testId = performanceTestService.startNewTest(request);
        log.info("Generated test ID: {}", testId);  // 로그 추가
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