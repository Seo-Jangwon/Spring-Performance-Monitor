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

/**
 * Aspect class for measuring and monitoring method performance.
 * This aspect intercepts methods annotated with @PerformanceMeasure and collects various metrics:
 * - Execution time
 * - Memory usage
 * - Thread metrics
 *
 * The collected data is stored through PerformanceMonitorService for analysis.
 *
 * @author Seo-Jangwon
 * @see com.monitor.annotation.annotation.PerformanceMeasure
 */
@Aspect
@Component
@RequiredArgsConstructor
public class PerformanceAspect {

    private final PerformanceMonitorService monitorService;
    private final ThreadMonitorService threadMonitorService;


    /**
     * Measures performance metrics for methods annotated with @PerformanceMeasure.
     * Collects the following metrics:
     * - Method execution time in milliseconds
     * - Memory usage before and after method execution
     * - Thread metrics during method execution
     *
     * @param joinPoint The join point representing the intercepted method
     * @param performanceMeasure The annotation instance containing measurement configuration
     * @return The result of the method execution
     * @throws Throwable If any error occurs during method execution
     */
    @Around("@annotation(performanceMeasure)")
    public Object measurePerformance(ProceedingJoinPoint joinPoint,
        PerformanceMeasure performanceMeasure) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        // Start monitoring the method thread
        ThreadMetrics threadMetrics = threadMonitorService.startMethodMonitoring(className, methodName);

        // Measure start time and memory
        long startTime = System.nanoTime();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        try {
            // Start method
            return joinPoint.proceed();
        } finally {

            // Measure end time and memory
            long executionTime = (System.nanoTime() - startTime) / 1_000_000;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = (endMemory - startMemory) / 1024;

            // Collect final thread metrics
            threadMonitorService.updateMethodMetrics(className, methodName);
            ThreadMetrics finalMetrics = threadMonitorService.getMethodMetrics(className, methodName);

            // Save performance data
            PerformanceData performanceData = PerformanceData.of(
                className,
                methodName,
                performanceMeasure.value(),
                executionTime,
                memoryUsed,
                finalMetrics
            );

            monitorService.addPerformanceData(performanceData);

            // End monitoring
            threadMonitorService.stopMethodMonitoring(className, methodName);
        }
    }
}