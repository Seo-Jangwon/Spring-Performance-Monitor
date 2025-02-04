/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.aop;

import com.monitor.annotation.annotation.PerformanceMeasure;
import com.monitor.annotation.model.PerformanceData;
import com.monitor.annotation.service.PerformanceMonitorService;
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

    @Around("@annotation(performanceMeasure)")
    public Object measurePerformance(ProceedingJoinPoint joinPoint,
        PerformanceMeasure performanceMeasure) throws Throwable {
        // 시작 시간과 메모리 측정
        long startTime = System.nanoTime();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        try {
            // 실제 메서드 실행
            return joinPoint.proceed();
        } finally {
            // 종료 시간과 메모리 측정
            long executionTime = (System.nanoTime() - startTime) / 1_000_000; // 나노초를 밀리초로 변환
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = (endMemory - startMemory) / 1024; // 바이트를 KB로 변환

            // 메서드 정보 추출
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String className = signature.getDeclaringType().getSimpleName();
            String methodName = signature.getName();

            // 성능 데이터 저장
            PerformanceData performanceData = PerformanceData.of(
                className,
                methodName,
                performanceMeasure.value(),
                executionTime,
                memoryUsed
            );

            monitorService.addPerformanceData(performanceData);
        }
    }
}