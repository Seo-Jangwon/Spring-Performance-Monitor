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

@Slf4j
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private final Map<WebSocketSession, String> sessionTestMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final MemoryMonitorService memoryMonitorService;
    private final PerformanceTestService testService;
    private final ThreadMonitorService threadMonitorService;

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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: {}", session.getId());
    }

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
                    // 통합된 메트릭 수집
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

    private WebSocketMessage createWebSocketMessage(TestResult testResult) {
        // 메모리 메트릭 수집
        MemoryMetrics metrics = memoryMonitorService.collectMetrics();
        testResult.addMemoryMetric(metrics);

        // 스레드 메트릭 업데이트
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

    @Getter
    @AllArgsConstructor
    private static class WebSocketMessage {

        private final TestResult testStatus;
        private final ThreadMetrics threadMetrics;
        private final MemoryMetrics metrics;
    }
}