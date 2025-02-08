/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.service;

import com.monitor.annotation.dto.MemoryMetrics;
import com.monitor.annotation.dto.ThreadMetrics;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.lang.management.*;
import java.util.List;
import java.util.Optional;

/**
 * Service for monitoring JVM memory metrics and garbage collection statistics.
 * Collects detailed metrics about:
 * - Heap memory usage (Young/Old generation)
 * - Non-heap memory usage (Metaspace)
 * - Garbage collection activities
 * - Thread pool statistics
 */
@Slf4j
@Service
public class MemoryMonitorService {

    private final MemoryMXBean memoryMXBean;
    private final List<MemoryPoolMXBean> memoryPoolMXBeans;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final ThreadMXBean threadMXBean;
    private final ThreadPoolTaskExecutor performanceTestExecutor;
    private final ThreadMonitorService threadMonitorService;

    /**
     * Initializes the service with required MXBeans and executors for monitoring.
     */
    public MemoryMonitorService(
        @Qualifier("performanceTestExecutor") ThreadPoolTaskExecutor performanceTestExecutor,
        ThreadMonitorService threadMonitorService
    ) {
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        this.gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.performanceTestExecutor = performanceTestExecutor;
        this.threadMonitorService = threadMonitorService;
    }

    /**
     * Collects comprehensive memory metrics including heap, non-heap, GC,
     * and thread pool statistics.
     *
     * @return Current memory metrics snapshot
     */
    public MemoryMetrics collectMetrics() {
        try {
            // collect base memory metrics
            MemoryMetrics baseMetrics = collectBaseMetrics();

            // collect and integrate threadMetrics
            ThreadMetrics threadMetrics = threadMonitorService.getMethodMetrics(
                "PerformanceTestService", "runTest"
            );

            // return integrated metrics
            return baseMetrics.withThreadMetrics(threadMetrics);

        } catch (Exception e) {
            log.error("Failed to collect metrics", e);
            return MemoryMetrics.empty();
        }
    }

    /**
     * Collects base memory metrics including heap, non-heap usage, and GC statistics.
     */
    private MemoryMetrics collectBaseMetrics() {
        // heap
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        // non heap
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        // Metaspace
        Optional<MemoryPoolMXBean> metaspace = memoryPoolMXBeans.stream()
            .filter(pool -> pool.getName().contains("Metaspace"))
            .findFirst();

        // collect Young/Old Generation memory usage
        long youngGenUsed = getMemoryPoolUsage("Eden Space")
            .map(MemoryUsage::getUsed)
            .orElse(0L);

        long oldGenUsed = getMemoryPoolUsage("Old Gen")
            .map(MemoryUsage::getUsed)
            .orElse(0L);

        // collect GC metrics
        GCMetrics gcMetrics = collectGCMetrics();

        return MemoryMetrics.builder()
            .timestamp(LocalDateTime.now())
            // 힙 메모리
            .heapUsed(heapUsage.getUsed())
            .heapMax(heapUsage.getMax())
            .youngGenUsed(youngGenUsed)
            .oldGenUsed(oldGenUsed)
            // 논힙 메모리
            .nonHeapUsed(nonHeapUsage.getUsed())
            .nonHeapCommitted(nonHeapUsage.getCommitted())
            .nonHeapMax(nonHeapUsage.getMax())
            // 메타스페이스
            .metaspaceUsed(metaspace.map(pool -> pool.getUsage().getUsed()).orElse(0L))
            .metaspaceCommitted(metaspace.map(pool -> pool.getUsage().getCommitted()).orElse(0L))
            // GC 메트릭
            .youngGcCount(gcMetrics.youngGcCount)
            .oldGcCount(gcMetrics.oldGcCount)
            .youngGcTime(gcMetrics.youngGcTime)
            .oldGcTime(gcMetrics.oldGcTime)
            // 스레드 상태
            .threadCount(threadMXBean.getThreadCount())
            .daemonThreadCount(threadMXBean.getDaemonThreadCount())
            .peakThreadCount(threadMXBean.getPeakThreadCount())
            .deadlockedThreads(Optional.ofNullable(threadMXBean.findDeadlockedThreads())
                .map(t -> t.length).orElse(0))
            .build();
    }

    @Getter
    @AllArgsConstructor
    private static class GCMetrics {

        private final long youngGcCount;
        private final long oldGcCount;
        private final long youngGcTime;
        private final long oldGcTime;
    }

    private GCMetrics collectGCMetrics() {
        long youngGcCount = 0;
        long oldGcCount = 0;
        long youngGcTime = 0;
        long oldGcTime = 0;

        for (GarbageCollectorMXBean gcBean : gcMXBeans) {
            if (gcBean.getName().contains("Young")) {
                youngGcCount += gcBean.getCollectionCount();
                youngGcTime += gcBean.getCollectionTime();
            } else if (gcBean.getName().contains("Old")) {
                oldGcCount += gcBean.getCollectionCount();
                oldGcTime += gcBean.getCollectionTime();
            }
        }

        return new GCMetrics(youngGcCount, oldGcCount, youngGcTime, oldGcTime);
    }

    private Optional<MemoryUsage> getMemoryPoolUsage(String poolName) {
        return memoryPoolMXBeans.stream()
            .filter(pool -> pool.getName().contains(poolName))
            .findFirst()
            .map(MemoryPoolMXBean::getUsage);
    }
}