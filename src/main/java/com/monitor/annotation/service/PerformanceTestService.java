/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.service;

import com.monitor.annotation.model.TestResult;
import com.monitor.annotation.model.TestScenarioRequest;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceTestService {

    private final RestTemplate restTemplate; // 실제 HTTP 요청 수행
    private final Map<String, TestResult> testResults = new ConcurrentHashMap<>(); // 테스트 결과 저장용 맵
    private final ExecutorService executorService = Executors.newCachedThreadPool(); // 동적으로 스레드 풀 크기 조절


    /**
     * 새로운 성능 테스트 시작
     * - 고유한 테스트 ID 생성
     * - 초기 테스트 결과 객체 생성 및 저장
     * - 비동기로 실제 테스트 실행
     */
    public String startNewTest(TestScenarioRequest request) {
        String testId = UUID.randomUUID().toString();

        log.info("Starting new test with ID: {}", testId);
        TestResult initialResult = TestResult.builder()
            .testId(testId)
            .description(request.getDescription())
            .url(request.getUrl())
            .method(request.getMethod())
            .completed(false)
            .startTime(LocalDateTime.now())
            .status("RUNNING")
            .build();

        testResults.put(testId, initialResult);
        log.info("Created initial result: {}", initialResult);

        executorService.submit(() -> runTest(testId, request));

        return testId;
    }

    /**
     * 특정 테스트 상태 조회
     */
    public TestResult getTestStatus(String testId) {
        return testResults.get(testId);
    }

    /**
     * 모든 테스트 결과 조회.
     */
    public List<TestResult> getAllTestResults() {
        return new ArrayList<>(testResults.values());
    }

    /**
     * 애플리케이션 종료 시 실행
     * ExecutorService 적절히 종료 -> 리소스 누수 방지
     */
    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 테스트 타임아웃 발생 시 처리
     * 타임아웃 상태의 결과 객체 생성, 저장
     */
    private void handleTestTimeout(String testId) {
        log.error("Test {} timed out", testId);
        TestResult currentResult = testResults.get(testId);
        if (currentResult != null) {
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
            testResults.put(testId, timeoutResult);
        }
    }

    /**
     * 실제 성능 테스트 실행
     * - 응답 시간 기록할 리스트 생성
     * - 모든 요청 완료될 때까지 대기하기 위한 CountDownLatch 설정
     * - 성공/실패 카운터 초기화
     * - HTTP 요청 실행 및 결과 수집
     * - 최종 결과 업데이트
     */
    private void runTest(String testId, TestScenarioRequest request) {
        List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(
            request.getConcurrentUsers() * request.getRepeatCount());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try {
            HttpHeaders headers = new HttpHeaders();
            request.getHeaders().forEach(headers::add);
            headers.add("X-Test-ID", testId);

            HttpEntity<?> requestEntity = createRequestEntity(request, headers);
            executeTestRequests(request, latch, responseTimes, successCount, failureCount,
                requestEntity);

            boolean completed = latch.await(request.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                handleTestTimeout(testId);
                return;
            }

            updateTestResults(testId, request, responseTimes, successCount, failureCount);

        } catch (Exception e) {
            log.error("Test {} failed: {}", testId, e.getMessage(), e);
            handleTestError(testId, e);
        }
    }

    /**
     * HTTP 요청에 사용할 Entity 객체 생성
     * RequestBody 있는 경우 JSON 형식으로 설정
     */
    private HttpEntity<?> createRequestEntity(TestScenarioRequest request, HttpHeaders headers) {
        if (request.getRequestBody() != null && !request.getRequestBody().isEmpty()) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(request.getRequestBody(), headers);
        }
        return new HttpEntity<>(headers);
    }

    /**
     * 설정된 동시 사용자 수만큼 요청 실행
     * RampUp이 설정된 경우 점진적으로 부하 증가
     */
    private void executeTestRequests(TestScenarioRequest request, CountDownLatch latch,
        List<Long> responseTimes, AtomicInteger successCount,
        AtomicInteger failureCount, HttpEntity<?> requestEntity) {
        for (int i = 0; i < request.getConcurrentUsers(); i++) {
            executorService.submit(() -> {
                if (request.getRampUpSeconds() > 0) {
                    applyRampUp(request.getRampUpSeconds());
                }
                executeUserRequests(request, latch, responseTimes, successCount, failureCount,
                    requestEntity);
            });
        }
    }

    /**
     * 각 사용자별 반복 요청 실행
     * - 요청 실행 시간 측정
     * - 응답 상태에 따른 성공/실패 카운트 증가
     * - 응답 시간 기록
     */
    private void executeUserRequests(TestScenarioRequest request, CountDownLatch latch,
        List<Long> responseTimes, AtomicInteger successCount,
        AtomicInteger failureCount, HttpEntity<?> requestEntity) {
        for (int j = 0; j < request.getRepeatCount(); j++) {
            try {
                long startTime = System.nanoTime();
                ResponseEntity<?> response = restTemplate.exchange(
                    request.getUrl(),
                    HttpMethod.valueOf(request.getMethod()),
                    requestEntity,
                    String.class
                );

                long responseTime = (System.nanoTime() - startTime) / 1_000_000;
                responseTimes.add(responseTime);

                if (response.getStatusCode().is2xxSuccessful()) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                    log.warn("Request failed with status: {}", response.getStatusCode());
                }
            } catch (Exception e) {
                failureCount.incrementAndGet();
                log.error("Request failed: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        }
    }

    /**
     * 테스트 실행 중 에러 발생 시 처리
     * 에러 상태의 결과 객체를 생성하여 저장
     */
    private void handleTestError(String testId, Exception e) {
        TestResult errorResult = TestResult.builder()
            .testId(testId)
            .description(testResults.get(testId).getDescription())
            .status("ERROR")
            .errorMessage(e.getMessage())
            .completed(true)
            .build();
        testResults.put(testId, errorResult);
    }

    /**
     * RampUp 적용을 위한 대기 시간 설정
     * 무작위 대기 시간으로 점진적 부하 증가
     */
    private void applyRampUp(int rampUpSeconds) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(rampUpSeconds * 1000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 최종 테스트 결과 업데이트
     * - 평균/최대/최소 응답 시간 계산
     * - 초당 요청 수 계산
     * - 에러율 계산
     * - 최종 결과 객체 생성 및 저장
     */
    private void updateTestResults(String testId, TestScenarioRequest request,
        List<Long> responseTimes, AtomicInteger successCount,
        AtomicInteger failureCount) {
        double avgResponseTime = responseTimes.stream()
            .mapToLong(Long::valueOf)
            .average()
            .orElse(0.0);

        double maxResponseTime = responseTimes.stream()
            .mapToLong(Long::valueOf)
            .max()
            .orElse(0);

        double minResponseTime = responseTimes.stream()
            .mapToLong(Long::valueOf)
            .min()
            .orElse(0);

        LocalDateTime endTime = LocalDateTime.now();
        double totalSeconds = java.time.Duration.between(
            testResults.get(testId).getStartTime(),
            endTime
        ).toMillis() / 1000.0;

        double requestsPerSecond = (successCount.get() + failureCount.get()) / totalSeconds;
        double errorRate = failureCount.get() * 100.0 / (successCount.get() + failureCount.get());

        TestResult finalResult = TestResult.builder()
            .testId(testId)
            .description(request.getDescription())
            .url(request.getUrl())
            .method(request.getMethod())
            .completed(true)
            .startTime(testResults.get(testId).getStartTime())
            .endTime(endTime)
            .totalRequests(successCount.get() + failureCount.get())
            .successfulRequests(successCount.get())
            .failedRequests(failureCount.get())
            .averageResponseTime(avgResponseTime)
            .maxResponseTime(maxResponseTime)
            .minResponseTime(minResponseTime)
            .requestsPerSecond(requestsPerSecond)
            .errorRate(errorRate)
            .responseTimes(responseTimes)
            .status("COMPLETED")
            .build();

        testResults.put(testId, finalResult);
    }
}