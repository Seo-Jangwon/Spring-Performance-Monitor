/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Builder
public class ThreadMetrics {
    private MetricType type;
    private String methodName;
    private String className;

    // 기본 스레드 정보
    private long threadId;
    private String threadName;
    private long threadCpuTime;
    private long threadUserTime;
    private Thread.State threadState;
    private boolean isDaemon;
    private int priority;
    private StackTraceElement[] stackTrace;

    // 스레드 풀 관련 메트릭
    private Integer poolSize;
    private Integer activePoolThreads;
    private Long queuedTasks;
    private Long completedTasks;

    // 메서드 내부 생성 스레드 추적
    private Map<Long, ThreadMetrics> childThreads;
    private long parentThreadId;
    private List<ThreadLifecycleEvent> lifecycleEvents;

    // 스레드 상태 통계
    private int runningThreadCount;
    private int blockedThreadCount;
    private int waitingThreadCount;
    private int timedWaitingThreadCount;

    public enum MetricType {
        SYSTEM,
        METHOD,
        CHILD_THREAD
    }

//    public static ThreadMetrics createMethodMetrics(String className, String methodName) {
//        return ThreadMetrics.builder()
//            .type(MetricType.METHOD)
//            .className(className)
//            .methodName(methodName)
//            .childThreads(new ConcurrentHashMap<>())
//            .build();
//    }

    public void addChildThread(ThreadMetrics childMetric) {
        if (this.childThreads != null) {
            this.childThreads.put(childMetric.getThreadId(), childMetric);
        }
    }

    public static ThreadMetrics empty() {
        return ThreadMetrics.builder()
            .type(MetricType.SYSTEM)
            .build();
    }
}

@Getter
@Builder
class ThreadLifecycleEvent {
    private long timestamp;
    private EventType type;
    private String description;

    public enum EventType {
        CREATED, STARTED, BLOCKED, WAITING, TERMINATED
    }
}