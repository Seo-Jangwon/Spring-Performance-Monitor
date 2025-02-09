/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.dto;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Builder
public class ThreadMetrics implements Cloneable {

    private MetricType type;
    private String methodName;
    private String className;

    // thread info
    private long threadId;
    private String threadName;
    private long threadCpuTime;
    private long threadUserTime;
    private Thread.State threadState;
    private boolean isDaemon;
    private int priority;
    private StackTraceElement[] stackTrace;

    // thread pool metrics
    private Integer poolSize;
    private Integer activePoolThreads;
    private Long queuedTasks;
    private Long completedTasks;

    // Method internal thread tracing
    private Map<Long, ThreadMetrics> childThreads;
    private long parentThreadId;
    private List<ThreadLifecycleEvent> lifecycleEvents;

    // thread state
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

    @Override
    public ThreadMetrics clone() {
        try {
            ThreadMetrics cloned = (ThreadMetrics) super.clone();

            // Deep copy childThreads
            if (this.childThreads != null) {
                Map<Long, ThreadMetrics> clonedChildren = new ConcurrentHashMap<>();
                this.childThreads.forEach((key, value) ->
                    clonedChildren.put(key, value.clone())
                );
                cloned.setChildThreads(clonedChildren);
            }

            // Deep copy stackTrace
            if (this.stackTrace != null) {
                cloned.setStackTrace(this.stackTrace.clone());
            }

            // Deep copy lifecycleEvents
            if (this.lifecycleEvents != null) {
                cloned.setLifecycleEvents(new ArrayList<>(this.lifecycleEvents));
            }

            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Failed to clone ThreadMetrics", e);
        }
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