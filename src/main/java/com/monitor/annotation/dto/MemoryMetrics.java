/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class MemoryMetrics {
    private LocalDateTime timestamp;

    // Heap Memory
    private long heapUsed;      // Currently used heap memory
    private long heapMax;       // Maximum heap memory
    private long youngGenUsed;  // Young Generation usage
    private long oldGenUsed;    // Old Generation usage

    // Non-Heap Memory
    private long nonHeapUsed;
    private long nonHeapCommitted;
    private long nonHeapMax;
    private long metaspaceUsed;
    private long metaspaceCommitted;

    // GC Metrics
    private long youngGcCount;  // Number of Young GC occurrences
    private long oldGcCount;    // Number of Full GC occurrences
    private long youngGcTime;   // Total time spent on Young GC
    private long oldGcTime;     // Total time spent on Full GC

    // Thread Metrics
    private int threadCount;        // Total number of threads
    private int daemonThreadCount;  // Number of daemon threads
    private int peakThreadCount;    // Peak thread count
    private int deadlockedThreads;  // Number of deadlocked threads

    // Performance Thread Pool Metrics
    @Builder.Default
    private ThreadPoolMetrics performanceThreadPool = ThreadPoolMetrics.empty();

    @Getter
    @Builder
    public static class ThreadPoolMetrics {
        private int activeThreads;      // Number of currently active threads
        private int poolSize;           // Current pool size
        private int corePoolSize;       // Core pool size
        private int maxPoolSize;        // Maximum pool size
        private long taskCount;         // Total number of tasks
        private long completedTaskCount; // Number of completed tasks
        private int queueSize;          // Size of the waiting queue
        private int waitingThreads;     // Number of threads in WAITING state
        private int blockedThreads;     // Number of threads in BLOCKED state
        private int runningThreads;     // Number of threads in RUNNABLE state

        public static ThreadPoolMetrics empty() {
            return ThreadPoolMetrics.builder()
                .activeThreads(0)
                .poolSize(0)
                .corePoolSize(0)
                .maxPoolSize(0)
                .taskCount(0)
                .completedTaskCount(0)
                .queueSize(0)
                .waitingThreads(0)
                .blockedThreads(0)
                .runningThreads(0)
                .build();
        }

        public static ThreadPoolMetrics from(ThreadMetrics threadMetrics) {
            if (threadMetrics == null) return empty();

            return ThreadPoolMetrics.builder()
                .activeThreads(threadMetrics.getActivePoolThreads())
                .poolSize(threadMetrics.getPoolSize())
                .corePoolSize(10)  // Value set in ThreadPoolConfig
                .maxPoolSize(50)   // Value set in ThreadPoolConfig
                .taskCount(threadMetrics.getQueuedTasks())
                .completedTaskCount(threadMetrics.getCompletedTasks())
                .queueSize(threadMetrics.getQueuedTasks().intValue())
                .waitingThreads(threadMetrics.getWaitingThreadCount())
                .blockedThreads(threadMetrics.getBlockedThreadCount())
                .runningThreads(threadMetrics.getRunningThreadCount())
                .build();
        }
    }

    public static MemoryMetrics empty() {
        return MemoryMetrics.builder()
            .timestamp(LocalDateTime.now())
            .performanceThreadPool(ThreadPoolMetrics.empty())
            .build();
    }

    // Integrate memory metrics and thread metrics
    public MemoryMetrics withThreadMetrics(ThreadMetrics threadMetrics) {
        if (threadMetrics == null) return this;

        // From ThreadMetrics to ThreadPoolMetrics
        ThreadPoolMetrics poolMetrics = ThreadPoolMetrics.from(threadMetrics);

        return MemoryMetrics.builder()
            .timestamp(this.timestamp)
            .heapUsed(this.heapUsed)
            .heapMax(this.heapMax)
            .youngGenUsed(this.youngGenUsed)
            .oldGenUsed(this.oldGenUsed)
            .nonHeapUsed(this.nonHeapUsed)
            .nonHeapCommitted(this.nonHeapCommitted)
            .nonHeapMax(this.nonHeapMax)
            .metaspaceUsed(this.metaspaceUsed)
            .metaspaceCommitted(this.metaspaceCommitted)
            .youngGcCount(this.youngGcCount)
            .oldGcCount(this.oldGcCount)
            .youngGcTime(this.youngGcTime)
            .oldGcTime(this.oldGcTime)
            .threadCount(this.threadCount)
            .daemonThreadCount(this.daemonThreadCount)
            .peakThreadCount(this.peakThreadCount)
            .deadlockedThreads(this.deadlockedThreads)
            .performanceThreadPool(poolMetrics)
            .build();
    }
}