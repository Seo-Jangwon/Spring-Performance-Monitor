/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monitor.annotation.dto.MemoryMetrics;
import com.monitor.annotation.dto.TestResult;
import com.monitor.annotation.dto.ThreadMetrics;
import com.monitor.annotation.service.MemoryMonitorService;
import com.monitor.annotation.service.PerformanceTestService;
import com.monitor.annotation.service.ThreadMonitorService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for streaming real-time performance metrics to clients.
 * Manages WebSocket connections and periodically sends metrics updates including:
 * - Memory usage metrics (heap, non-heap)
 * - Thread pool statistics
 * - Test execution progress
 * - Performance metrics
 */
@Slf4j
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private final Map<WebSocketSession, String> sessionTestMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final MemoryMonitorService memoryMonitorService;
    private final PerformanceTestService testService;
    private final ThreadMonitorService threadMonitorService;

    /**
     * Initializes the handler with required services and configures ObjectMapper with proper
     * datetime serialization settings.
     */
    public MetricsWebSocketHandler(
        MemoryMonitorService memoryMonitorService,
        PerformanceTestService testService,
        ThreadMonitorService threadMonitorService
    ) {
        this.memoryMonitorService = memoryMonitorService;
        this.testService = testService;
        this.threadMonitorService = threadMonitorService;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    /**
     * Handles WebSocket connection establishment.
     * Logs new connection information for debugging purposes.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
    }

    /**
     * Processes incoming messages from clients.
     * Expects test ID as the message payload and associates it with the session.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String testId = message.getPayload();
            sessionTestMap.put(session, testId);
            log.info("Received testId: {} for session: {}", testId, session.getId());
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

    /**
     * Scheduled task that sends metric updates to all connected clients.
     * Runs every second to collect and send
     * - Current test status
     * - Memory metrics
     * - Thread metrics
     *
     * Automatically closes the session when the test is completed.
     */
    @Scheduled(fixedRate = 1000)
    public void sendMetrics() {
        sessionTestMap.forEach((session, testId) -> {
            try {
                if (!session.isOpen()) {
                    sessionTestMap.remove(session);
                    return;
                }

                TestResult testResult = testService.getTestStatus(testId);
                if (testResult != null) {
                    // Integrated metric collection
                    WebSocketMessage message = createWebSocketMessage(testResult);

                    String payload = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(payload));

                    log.debug("Sent metrics to session {} with message: {}",
                        session.getId(), message);

                    if (testResult.isCompleted()) {
                        log.info("Test completed for session: {}", session.getId());
                        Thread.sleep(1000);  // 마지막 메트릭 보내기 위한 대기
                        session.close();
                        sessionTestMap.remove(session);
                    }
                } else {
                    session.close();
                    sessionTestMap.remove(session);
                }
            } catch (Exception e) {
                log.error("Failed to send metrics to session {}: {}", session.getId(), e);
            }
        });
    }

    /**
     * Creates a comprehensive message containing all metrics for a test.
     * Includes:
     * - Current test status and results
     * - Memory usage metrics
     * - Thread pool statistics
     *
     * @param testResult Current test result data
     * @return WebSocketMessage containing all metrics
     */
    private WebSocketMessage createWebSocketMessage(TestResult testResult) {
        // Memory metric collection
        MemoryMetrics metrics = memoryMonitorService.collectMetrics();
        testResult.addMemoryMetric(metrics);

        // Thread metric update
        ThreadMetrics threadMetrics = null;
        if (!testResult.isCompleted()) {
            threadMetrics = threadMonitorService.getMethodMetrics(
                testResult.getClassName(),
                testResult.getMethodName()
            );
            testResult.updateThreadMetrics(threadMetrics);
        }

        return new WebSocketMessage(testResult, threadMetrics, metrics);
    }

    /**
     * Data transfer object for WebSocket messages.
     * Contains all metrics data to be sent to clients.
     */
    @Getter
    @AllArgsConstructor
    private static class WebSocketMessage {

        private final TestResult testStatus;
        private final ThreadMetrics threadMetrics;
        private final MemoryMetrics metrics;
    }
}