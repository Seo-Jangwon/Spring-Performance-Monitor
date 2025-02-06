/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.service;

import com.monitor.annotation.dto.MemoryMetrics;
import com.monitor.annotation.dto.TestResult;
import com.monitor.annotation.dto.TestScenarioRequest;
import com.monitor.annotation.dto.ThreadMetrics;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceTestService {

    private final RestTemplate restTemplate;
    private final MemoryMonitorService memoryMonitorService;    // 메모리 모니터링
    private final ThreadMonitorService threadMonitorService;    // 스레드 모니터링

    @Qualifier("performanceTestExecutor")
    private final ThreadPoolTaskExecutor performanceTestExecutor;

    // 테스트 결과 및 메트릭 저장소
    private final Map<String, TestResult> testResults = new ConcurrentHashMap<>();           // 테스트 결과 저장
    private final Map<String, List<MemoryMetrics>> activeTestMetrics = new ConcurrentHashMap<>();  // 활성 테스트의 메모리 메트릭

    /**
     * 활성화된 테스트들 메모리 메트릭 주기적으로 수집. 1초마다 실행. 현재 실행 중인 모든 테스트에 대한 메모리 메트릭 수집
     */
    @Scheduled(fixedRate = 1000)
    public void collectMetricsForActiveTests() {
        if (!activeTestMetrics.isEmpty()) {
            MemoryMetrics currentMetrics = memoryMonitorService.collectMetrics();
            activeTestMetrics.forEach((testId, metricsList) -> metricsList.add(currentMetrics));
        }
    }

    /**
     * 새로운 테스트에 대한 메트릭 수집 시작
     *
     * @param testId 테스트 ID
     */
    private void startMetricsCollection(String testId) {
        activeTestMetrics.put(testId, Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * 테스트 메트릭 수집 중단 및 수집된 메트릭 반환
     *
     * @param testId 테스트 ID
     * @return 수집된 메모리 메트릭 리스트
     */
    private List<MemoryMetrics> stopMetricsCollection(String testId) {
        return activeTestMetrics.remove(testId);
    }

    /**
     * 새로운 성능 테스트를 시작
     *
     * @param request 테스트 시나리오 요청 정보
     * @return 생성된 테스트 ID
     */
    public String startNewTest(TestScenarioRequest request) {
        String testId = UUID.randomUUID().toString();
        startMetricsCollection(testId);

        log.info("Starting new test with ID: {}", testId);

        TestResult initialResult = TestResult.builder()
            .testId(testId)
            .description(request.getDescription())
            .url(request.getUrl())
            .method(request.getMethod())
            .className(this.getClass().getSimpleName())
            .methodName("runTest")
            .completed(false)
            .startTime(LocalDateTime.now())
            .status("RUNNING")
            .build();

        testResults.put(testId, initialResult);
        log.info("Created initial result: {}", initialResult);

        performanceTestExecutor.submit(() -> runTest(testId, request));

        return testId;
    }

    /**
     * 특정 테스트의 현재 상태를 조회
     */
    public TestResult getTestStatus(String testId) {
        return testResults.get(testId);
    }

    /**
     * 모든 테스트 결과 조회
     */
    public List<TestResult> getAllTestResults() {
        return new ArrayList<>(testResults.values());
    }

    /**
     * 애플리케이션 종료 시 실행 스레드 풀 안전하게 종료하여 리소스 누수 방지
     */
    @PreDestroy
    public void shutdown() {
        performanceTestExecutor.shutdown();
    }

    /**
     * 실제 성능 테스트를 실행
     *
     * @param testId  테스트 ID
     * @param request 테스트 시나리오 요청 정보
     */
    private void runTest(String testId, TestScenarioRequest request) {
        ThreadMetrics threadMetrics = null;
        try {
            threadMetrics = threadMonitorService.startMethodMonitoring(
                this.getClass().getSimpleName(),
                "runTest"
            );

            List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(
                request.getConcurrentUsers() * request.getRepeatCount());
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            HttpHeaders headers = prepareHeaders(request, testId);
            HttpEntity<?> requestEntity = createRequestEntity(request, headers);

            executeTestRequests(request, latch, responseTimes, successCount, failureCount,
                requestEntity, testId);

            if (!waitForTestCompletion(testId, request, latch)) {
                return;
            }

            updateFinalResults(testId, request, responseTimes, successCount.get(),
                failureCount.get());

        } catch (Exception e) {
            log.error("Test {} failed: {}", testId, e.getMessage(), e);
            handleTestError(testId, e);
        } finally {
            if (threadMetrics != null) {
                threadMonitorService.stopMethodMonitoring(
                    this.getClass().getSimpleName(),
                    "runTest"
                );
            }
        }
    }

    /**
     * HTTP 요청 헤더 준비
     */
    private HttpHeaders prepareHeaders(TestScenarioRequest request, String testId) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaders().forEach(headers::add);
        headers.add("X-Test-ID", testId);
        return headers;
    }

    /**
     * HTTP 요청 Entity 생성
     */
    private HttpEntity<?> createRequestEntity(TestScenarioRequest request, HttpHeaders headers) {
        if (request.getRequestBody() != null && !request.getRequestBody().isEmpty()) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(request.getRequestBody(), headers);
        }
        return new HttpEntity<>(headers);
    }

    /**
     * 테스트 요청 실행
     */
    private void executeTestRequests(TestScenarioRequest request, CountDownLatch latch,
        List<Long> responseTimes, AtomicInteger successCount, AtomicInteger failureCount,
        HttpEntity<?> requestEntity, String testId) {

        for (int i = 0; i < request.getConcurrentUsers(); i++) {
            int userId = i;
            performanceTestExecutor.submit(() -> {
                try {
                    if (request.getRampUpSeconds() > 0) {
                        Thread.sleep(calculateRampUpDelay(userId, request.getRampUpSeconds(),
                            request.getConcurrentUsers()));
                    }
                    executeUserRequests(request, latch, responseTimes, successCount, failureCount,
                        requestEntity, testId);
                } catch (Exception e) {
                    log.error("User thread execution failed: {}", e.getMessage(), e);
                    handleFailedRequests(request.getRepeatCount(), failureCount, latch);
                }
            });
        }
    }

    /**
     * 실패한 요청 처리
     */
    private void handleFailedRequests(int remainingRequests, AtomicInteger failureCount,
        CountDownLatch latch) {
        for (int j = 0; j < remainingRequests; j++) {
            failureCount.incrementAndGet();
            latch.countDown();
        }
    }

    /**
     * 테스트 완료 대기
     *
     * @return 정상 완료 여부
     */
    private boolean waitForTestCompletion(String testId, TestScenarioRequest request,
        CountDownLatch latch) {
        try {
            if (!latch.await(request.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                handleTestTimeout(testId);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleTestError(testId, e);
            return false;
        }
    }

    /**
     * 단일 사용자의 반복 요청 실행
     */
    private void executeUserRequests(TestScenarioRequest request, CountDownLatch latch,
        List<Long> responseTimes, AtomicInteger successCount, AtomicInteger failureCount,
        HttpEntity<?> requestEntity, String testId) {

        for (int j = 0; j < request.getRepeatCount(); j++) {
            try {
                executeRequest(request, responseTimes, successCount, failureCount, requestEntity, testId);
            } catch (Exception e) {
                log.error("Request failed: {}", e.getMessage());
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * 단일 HTTP 요청 실행
     */
    private void executeRequest(TestScenarioRequest request, List<Long> responseTimes,
        AtomicInteger successCount, AtomicInteger failureCount, HttpEntity<?> requestEntity, String testId) {

        long startTime = System.nanoTime();
        ResponseEntity<?> response = restTemplate.exchange(
            request.getUrl(),
            HttpMethod.valueOf(request.getMethod()),
            requestEntity,
            String.class
        );
        long responseTime = (System.nanoTime() - startTime) / 1_000_000;

        responseTimes.add(responseTime);
        TestResult currentResult = testResults.get(testId);

        if (currentResult != null) {
            currentResult.addResponseTime(responseTime);

            if (response.getStatusCode().is2xxSuccessful()) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }

            // 실시간 진행 상황 업데이트
            currentResult.updateProgress(
                successCount.get() + failureCount.get(),
                successCount.get(),
                failureCount.get()
            );
        }
    }

    /**
     * 램프업 딜레이 계산
     */
    private long calculateRampUpDelay(int userIndex, int rampUpSeconds, int totalUsers) {
        return (long) (((double) userIndex / totalUsers) * rampUpSeconds * 1000);
    }

    /**
     * 타임아웃 발생 시 처리
     */
    private void handleTestTimeout(String testId) {
        log.error("Test {} timed out", testId);
        TestResult currentResult = testResults.get(testId);
        if (currentResult != null) {
            List<MemoryMetrics> metrics = stopMetricsCollection(testId);
            TestResult timeoutResult = TestResult.builder()
                .testId(testId)
                .description(currentResult.getDescription())
                .url(currentResult.getUrl())
                .method(currentResult.getMethod())
                .completed(true)
                .startTime(currentResult.getStartTime())
                .endTime(LocalDateTime.now())
                .totalRequests(0)
                .successfulRequests(0)
                .failedRequests(0)
                .errorRate(100.0)
                .status("TIMEOUT")
                .build();

            if (metrics != null) {
                metrics.forEach(timeoutResult::addMemoryMetric);
            }

            testResults.put(testId, timeoutResult);
        }
    }

    /**
     * 테스트 에러 발생 시 처리
     */
    private void handleTestError(String testId, Exception e) {
        List<MemoryMetrics> metrics = stopMetricsCollection(testId);
        TestResult errorResult = TestResult.builder()
            .testId(testId)
            .description(testResults.get(testId).getDescription())
            .status("ERROR")
            .errorMessage(e.getMessage())
            .completed(true)
            .build();

        if (metrics != null) {
            metrics.forEach(errorResult::addMemoryMetric);
        }

        testResults.put(testId, errorResult);
    }

    /**
     * 최종 테스트 결과 업데이트
     */
    private void updateFinalResults(String testId, TestScenarioRequest request,
        List<Long> responseTimes, int successCount, int failureCount) {

        List<MemoryMetrics> metrics = stopMetricsCollection(testId);
        LocalDateTime endTime = LocalDateTime.now();
        TestResult currentResult = testResults.get(testId);

        double totalSeconds = java.time.Duration.between(currentResult.getStartTime(), endTime)
            .toMillis() / 1000.0;

        TestResult finalResult = TestResult.builder()
            .testId(testId)
            .description(request.getDescription())
            .url(request.getUrl())
            .method(request.getMethod())
            .className(this.getClass().getSimpleName())
            .methodName("runTest")
            .completed(true)
            .startTime(currentResult.getStartTime())
            .endTime(endTime)
            .totalRequests(successCount + failureCount)
            .successfulRequests(successCount)
            .failedRequests(failureCount)
            .averageResponseTime(calculateAverageResponseTime(responseTimes))
            .maxResponseTime(calculateMaxResponseTime(responseTimes))
            .minResponseTime(calculateMinResponseTime(responseTimes))
            .requestsPerSecond((successCount + failureCount) / totalSeconds)
            .errorRate(calculateErrorRate(successCount, failureCount))
            .status("COMPLETED")
            .build();

        responseTimes.forEach(finalResult::addResponseTime);
        if (metrics != null) {
            metrics.forEach(finalResult::addMemoryMetric);
        }

        testResults.put(testId, finalResult);
    }

    /**
     * 평균 응답 시간 계산
     */
    private double calculateAverageResponseTime(List<Long> responseTimes) {
        return responseTimes.stream()
            .mapToLong(Long::valueOf)
            .average()
            .orElse(0.0);
    }

    /**
     * 최대 응답 시간 계산
     */
    private double calculateMaxResponseTime(List<Long> responseTimes) {
        return responseTimes.stream()
            .mapToLong(Long::valueOf)
            .max()
            .orElse(0);
    }

    /**
     * 최소 응답 시간 계산
     */
    private double calculateMinResponseTime(List<Long> responseTimes) {
        return responseTimes.stream()
            .mapToLong(Long::valueOf)
            .min()
            .orElse(0);
    }

    /**
     * 에러율 계산
     */
    private double calculateErrorRate(int successCount, int failureCount) {
        return failureCount * 100.0 / (successCount + failureCount);
    }
}