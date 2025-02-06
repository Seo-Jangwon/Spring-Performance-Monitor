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
    private long heapUsed;      // 현재 사용 중인 힙 메모리
    private long heapMax;       // 최대 힙 메모리
    private long youngGenUsed;  // Young Generation 사용량
    private long oldGenUsed;    // Old Generation 사용량

    // Non-Heap Memory
    private long nonHeapUsed;
    private long nonHeapCommitted;
    private long nonHeapMax;
    private long metaspaceUsed;
    private long metaspaceCommitted;

    // GC Metrics
    private long youngGcCount;  // Young GC 발생 횟수
    private long oldGcCount;    // Full GC 발생 횟수
    private long youngGcTime;   // Young GC 총 소요 시간
    private long oldGcTime;     // Full GC 총 소요 시간

    // Thread Metrics
    private int threadCount;        // 전체 쓰레드 수
    private int daemonThreadCount;  // 데몬 쓰레드 수
    private int peakThreadCount;    // 피크 쓰레드 수
    private int deadlockedThreads;  // 데드락 상태 쓰레드 수

    // Performance Thread Pool Metrics
    @Builder.Default
    private ThreadPoolMetrics performanceThreadPool = ThreadPoolMetrics.empty();

    @Getter
    @Builder
    public static class ThreadPoolMetrics {
        private int activeThreads;      // 현재 활성 쓰레드 수
        private int poolSize;           // 현재 풀 크기
        private int corePoolSize;       // 코어 풀 크기
        private int maxPoolSize;        // 최대 풀 크기
        private long taskCount;         // 총 작업 수
        private long completedTaskCount;// 완료된 작업 수
        private int queueSize;          // 대기 큐 크기
        private int waitingThreads;     // WAITING 상태 쓰레드 수
        private int blockedThreads;     // BLOCKED 상태 쓰레드 수
        private int runningThreads;     // RUNNABLE 상태 쓰레드 수

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
                .corePoolSize(10)  // ThreadPoolConfig에서 설정된 값
                .maxPoolSize(50)   // ThreadPoolConfig에서 설정된 값
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

    // 메모리 메트릭과 스레드 메트릭 통합
    public MemoryMetrics withThreadMetrics(ThreadMetrics threadMetrics) {
        if (threadMetrics == null) return this;

        // ThreadMetrics에서 ThreadPoolMetrics로
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