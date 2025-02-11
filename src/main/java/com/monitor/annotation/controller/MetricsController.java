/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.controller;

import com.monitor.annotation.dto.MemoryMetrics;
import com.monitor.annotation.dto.TestResult;
import com.monitor.annotation.dto.ThreadMetrics;
import com.monitor.annotation.service.MemoryMonitorService;
import com.monitor.annotation.service.PerformanceTestService;
import com.monitor.annotation.service.ThreadMonitorService;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/performanceMeasure/metrics")
@RequiredArgsConstructor
@Slf4j
public class MetricsController {

    private final MemoryMonitorService memoryMonitorService;
    private final PerformanceTestService performanceTestService;
    private final ThreadMonitorService threadMonitorService;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Qualifier("taskScheduler")
    private final TaskScheduler taskScheduler;

    @GetMapping(path = "/stream/{testId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMetrics(@PathVariable String testId) {
        log.info("SSE Stream requested for test: {}", testId);
        SseEmitter emitter = new SseEmitter(180_000L); // 3분 타임아웃

        try {
            // 이전 emitter가 있다면 정리
            removeEmitter(testId);

            emitters.put(testId, emitter);
            log.info("Started metrics stream for test: {}", testId);

            // 비동기로 메트릭 전송 시작
            startMetricsEmission(testId, emitter);

            // 완료, 타임아웃, 에러 처리
            emitter.onCompletion(() -> {
                log.info("Metrics stream completed for test: {}", testId);
                removeEmitter(testId);
            });

            emitter.onTimeout(() -> {
                log.warn("Metrics stream timed out for test: {}", testId);
                removeEmitter(testId);
            });

            emitter.onError(ex -> {
                log.error("Error in metrics stream for test: {}", testId, ex);
                removeEmitter(testId);
            });

        } catch (Exception e) {
            log.error("Error creating metrics stream for test: {}", testId, e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    private void removeEmitter(String testId) {
        // 스케줄된 태스크 취소
        ScheduledFuture<?> task = scheduledTasks.remove(testId);
        if (task != null) {
            task.cancel(true);
        }

        SseEmitter oldEmitter = emitters.remove(testId);
        if (oldEmitter != null) {
            try {
                oldEmitter.complete();
            } catch (Exception e) {
                log.warn("Error completing old emitter for test: {}", testId, e);
            }
        }
    }

    private void startMetricsEmission(String testId, SseEmitter emitter) {
        log.info("Starting metrics emission for test: {}", testId);
        ScheduledFuture<?> task = taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                TestResult testResult = performanceTestService.getTestStatus(testId);
                if (testResult == null) {
                    log.warn("No test result found for test: {}", testId);
                    removeEmitter(testId);
                    return;
                }

                log.debug("Creating metrics message for test: {}, status: {}", testId,
                    testResult.getStatus());
                MetricsMessage message = createMetricsMessage(testResult);
                emitter.send(message);
                log.debug("Sent metrics message for test: {}", testId);

                if (testResult.isCompleted()) {
                    log.info("Test completed, closing stream for test: {}", testId);
                    removeEmitter(testId);
                }
            } catch (Exception e) {
                log.error("Error sending metrics for test: {}", testId, e);
                removeEmitter(testId);
            }
        }, Duration.ofSeconds(1));

        scheduledTasks.put(testId, task);
    }

    private MetricsMessage createMetricsMessage(TestResult testResult) {
        log.info("Creating metrics message for test: {}, status: {}",
            testResult.getTestId(), testResult.getStatus());

        MemoryMetrics metrics = memoryMonitorService.collectMetrics();
        testResult.addMemoryMetric(metrics);

        ThreadMetrics threadMetrics = null;
        if (!testResult.isCompleted()) {
            threadMetrics = threadMonitorService.getMethodMetrics(
                testResult.getClassName(),
                testResult.getMethodName()
            );
            testResult.updateThreadMetrics(threadMetrics);
        }

        TestStatus status;
        switch (testResult.getStatus()) {
            case "START_TEST":
                status = TestStatus.START_TEST;
                break;
            case "STOP_TEST":
                status = TestStatus.STOP_TEST;
                break;
            default:
                status = testResult.isCompleted() ? TestStatus.COMPLETED : TestStatus.RUNNING;
        }

        log.info("Determined TestStatus: {}", status);
        return new MetricsMessage(status, testResult, threadMetrics, metrics);
    }

    @Getter
    @AllArgsConstructor
    private static class MetricsMessage {

        private final TestStatus status;
        private final TestResult testStatus;
        private final ThreadMetrics threadMetrics;
        private final MemoryMetrics metrics;
    }

    public enum TestStatus {
        RUNNING,
        START_TEST,
        STOP_TEST,
        COMPLETED
    }
}