/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.aop;

import com.monitor.annotation.annotation.PerformanceMeasure;
import com.monitor.annotation.dto.PerformanceData;
import com.monitor.annotation.dto.ThreadMetrics;
import com.monitor.annotation.service.PerformanceMonitorService;
import com.monitor.annotation.service.ThreadMonitorService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class PerformanceAspect {

    private final PerformanceMonitorService monitorService;
    private final ThreadMonitorService threadMonitorService;

    @Around("@annotation(performanceMeasure)")
    public Object measurePerformance(ProceedingJoinPoint joinPoint,
        PerformanceMeasure performanceMeasure) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // 메서드 스레드 모니터링 시작
        ThreadMetrics threadMetrics = threadMonitorService.startMethodMonitoring(className, methodName);

        // 시작 시간과 메모리 측정
        long startTime = System.nanoTime();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        try {
            // 실제 메서드 실행
            return joinPoint.proceed();
        } finally {
            // 종료 시간과 메모리 측정
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = (endMemory - startMemory) / 1024;

            // 최종 스레드 메트릭 수집
            threadMonitorService.updateMethodMetrics(className, methodName);
            ThreadMetrics finalMetrics = threadMonitorService.getMethodMetrics(className, methodName);

            // 성능 데이터 저장
            PerformanceData performanceData = PerformanceData.of(
                className,
                methodName,
                performanceMeasure.value(),
                executionTime,
                memoryUsed,
                finalMetrics
            );

            monitorService.addPerformanceData(performanceData);

            // 모니터링 종료
            threadMonitorService.stopMethodMonitoring(className, methodName);
        }
    }
}