/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.service;

import com.monitor.annotation.model.MemoryMetrics;
import com.monitor.annotation.model.ThreadMetrics;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.lang.management.*;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class MemoryMonitorService {

    private final MemoryMXBean memoryMXBean;
    private final List<MemoryPoolMXBean> memoryPoolMXBeans;
    private final List<GarbageCollectorMXBean> gcMXBeans;
    private final ThreadMXBean threadMXBean;
    private final ThreadPoolTaskExecutor performanceTestExecutor;
    private final ThreadMonitorService threadMonitorService;

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

    public MemoryMetrics collectMetrics() {
        try {
            // 기본 메모리 메트릭 수집
            MemoryMetrics baseMetrics = collectBaseMetrics();

            // ThreadMetrics 수집 및 통합
            ThreadMetrics threadMetrics = threadMonitorService.getMethodMetrics(
                "PerformanceTestService", "runTest"
            );

            // 통합된 메트릭 반환
            return baseMetrics.withThreadMetrics(threadMetrics);

        } catch (Exception e) {
            log.error("Failed to collect metrics", e);
            return MemoryMetrics.empty();
        }
    }

    private MemoryMetrics collectBaseMetrics() {
        // 힙 메모리
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        // 논힙 메모리
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        // 메타스페이스
        Optional<MemoryPoolMXBean> metaspace = memoryPoolMXBeans.stream()
            .filter(pool -> pool.getName().contains("Metaspace"))
            .findFirst();

        // Young/Old Generation 메모리 사용량 수집
        long youngGenUsed = getMemoryPoolUsage("Eden Space")
            .map(MemoryUsage::getUsed)
            .orElse(0L);

        long oldGenUsed = getMemoryPoolUsage("Old Gen")
            .map(MemoryUsage::getUsed)
            .orElse(0L);

        // GC 메트릭 수집
        GCMetrics gcMetrics = collectGCMetrics();

        // 스레드 상태
        ThreadStates threadStates = collectThreadStates();

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

    @Getter
    @AllArgsConstructor
    private static class ThreadStates {

        private final int waiting;
        private final int blocked;
        private final int running;
    }

    private ThreadStates collectThreadStates() {
        int waiting = 0;
        int blocked = 0;
        int running = 0;

        ThreadInfo[] threadInfo = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds());
        for (ThreadInfo info : threadInfo) {
            if (info != null) {
                switch (info.getThreadState()) {
                    case WAITING:
                    case TIMED_WAITING:
                        waiting++;
                        break;
                    case BLOCKED:
                        blocked++;
                        break;
                    case RUNNABLE:
                        running++;
                        break;
                }
            }
        }

        return new ThreadStates(waiting, blocked, running);
    }

    private Optional<MemoryUsage> getMemoryPoolUsage(String poolName) {
        return memoryPoolMXBeans.stream()
            .filter(pool -> pool.getName().contains(poolName))
            .findFirst()
            .map(MemoryPoolMXBean::getUsage);
    }
}