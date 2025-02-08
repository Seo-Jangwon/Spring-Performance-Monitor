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

/**
 * Controller for managing performance tests of REST endpoints.
 * Provides functionality for:
 * - Displaying available endpoints for testing
 * - Executing performance tests
 * - Monitoring test progress
 * - Retrieving test results
 *
 * The controller works in conjunction with the frontend dashboard to provide
 * real-time visualization of test execution and results.
 */
@Slf4j
@Controller
@RequestMapping("/performanceMeasure")
@RequiredArgsConstructor
public class PerformanceTestController {

    private final PerformanceTestService performanceTestService;
    private final PerformanceEndpointScanner scanner;

    /**
     * Displays the main performance test dashboard page.
     * Scans and displays all available endpoints that can be tested,
     * along with their existing test results.
     *
     * @param model Model for passing data to the view
     * @return View name for the dashboard page
     */
    @GetMapping
    public String showTestPage(Model model) {
        Map<String, List<PerformanceEndpoint>> endpoints = scanner.scanEndpoints();
        log.info("Found endpoints for UI: {}", endpoints);
        model.addAttribute("testScenarios", performanceTestService.getAllTestResults());
        model.addAttribute("endpoints", endpoints);
        return "performanceMeasure/index";
    }

    /**
     * REST endpoint that returns all available endpoints for testing.
     * Used by the frontend to dynamically update the available endpoints list.
     *
     * @return Map of HTTP methods to their corresponding endpoints
     */
    @GetMapping("/endpoints")
    @ResponseBody
    public ResponseEntity<Map<String, List<PerformanceEndpoint>>> getEndpoints() {
        Map<String, List<PerformanceEndpoint>> endpoints = scanner.scanEndpoints();
        return ResponseEntity.ok(endpoints);
    }

    /**
     * Initiates a new performance test based on the provided test scenario.
     * Generates a unique test ID and starts the test asynchronously.
     *
     * @param request Test scenario configuration including endpoint, concurrent users, etc.
     * @return Generated test ID for tracking test progress
     */
    @PostMapping("/run")
    @ResponseBody
    public String runTest(@RequestBody TestScenarioRequest request) {
        log.info("Received test request: {}", request);
        String testId = performanceTestService.startNewTest(request);
        log.info("Generated test ID: {}", testId);
        return testId;
    }

    /**
     * Retrieves the current status of a specific test.
     * Used by the frontend to poll test progress and update the UI.
     *
     * @param testId ID of the test to check
     * @return Current status of the test including metrics and results
     */
    @GetMapping("/status/{testId}")
    @ResponseBody
    public Object getTestStatus(@PathVariable String testId) {
        return performanceTestService.getTestStatus(testId);
    }

    /**
     * Retrieves all test results.
     * Used to display historical test data and comparisons.
     *
     * @return List of all test results
     */
    @GetMapping("/results")
    @ResponseBody
    public List<TestResult> getResults() {
        return performanceTestService.getAllTestResults();
    }
}