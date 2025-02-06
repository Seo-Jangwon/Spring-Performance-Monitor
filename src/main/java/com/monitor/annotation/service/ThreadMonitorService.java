/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.service;

import com.monitor.annotation.dto.ThreadMetrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class ThreadMonitorService {

    private final ThreadMXBean threadMXBean;
    private final ThreadPoolTaskExecutor performanceExecutor;
    private final Map<String, ThreadMetrics> methodMetrics = new ConcurrentHashMap<>();
    private final Map<Long, String> threadToMethod = new ConcurrentHashMap<>();

    public ThreadMonitorService(
        @Qualifier("performanceTestExecutor") ThreadPoolTaskExecutor performanceExecutor) {
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.performanceExecutor = performanceExecutor;
    }

    @PostConstruct
    public void init() {
        threadMXBean.setThreadCpuTimeEnabled(true);
        threadMXBean.setThreadContentionMonitoringEnabled(true);
    }

    public ThreadMetrics startMethodMonitoring(String className, String methodName) {
        String key = className + "." + methodName;
        Thread currentThread = Thread.currentThread();
        long threadId = currentThread.getId();

        ThreadMetrics metrics = ThreadMetrics.builder()
            .type(ThreadMetrics.MetricType.METHOD)
            .className(className)
            .methodName(methodName)
            .threadId(threadId)
            .threadName(currentThread.getName())
            .threadState(currentThread.getState())
            .isDaemon(currentThread.isDaemon())
            .priority(currentThread.getPriority())
            .stackTrace(currentThread.getStackTrace())
            .childThreads(new ConcurrentHashMap<>())
            .build();

        updateMetrics(metrics);
        methodMetrics.put(key, metrics);
        threadToMethod.put(threadId, key);

        return metrics;
    }

    public void updateMethodMetrics(String className, String methodName) {
        String key = className + "." + methodName;
        ThreadMetrics metrics = methodMetrics.get(key);

        if (metrics != null) {
            updateMetrics(metrics);
            log.debug("Updated method metrics for {}: active={}, pool={}, queue={}, completed={}",
                key, metrics.getActivePoolThreads(), metrics.getPoolSize(),
                metrics.getQueuedTasks(), metrics.getCompletedTasks());
        }
    }

    private void updateMetrics(ThreadMetrics metrics) {
        if (metrics == null) {
            return;
        }

        // 스레드 상태 업데이트
        updateThreadStates(metrics);
        // CPU 시간 업데이트
        updateCpuTimes(metrics);
        // 스레드 풀 메트릭 업데이트
        updateThreadPoolMetrics(metrics);
    }

    private void updateThreadPoolMetrics(ThreadMetrics metrics) {
        if (!"PerformanceTestService".equals(metrics.getClassName())) {
            return;  // 성능 테스트 서비스의 메트릭만 업데이트
        }

        ThreadPoolExecutor executor = performanceExecutor.getThreadPoolExecutor();

        // 스레드 풀 기본 메트릭
        metrics.setActivePoolThreads(executor.getActiveCount());
        metrics.setPoolSize(executor.getPoolSize());
        metrics.setQueuedTasks((long) executor.getQueue().size());
        metrics.setCompletedTasks(executor.getCompletedTaskCount());

        // 스레드 상태 분석
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds());
        int waiting = 0;
        int blocked = 0;
        int running = executor.getActiveCount();

        // PerfTest- 접두사를 가진 스레드만 카운트
        for (ThreadInfo info : threadInfos) {
            if (info != null && info.getThreadName().startsWith("PerfTest-")) {
                switch (info.getThreadState()) {
                    case WAITING:
                    case TIMED_WAITING:
                        waiting++;
                        break;
                    case BLOCKED:
                        blocked++;
                        break;
                }
            }
        }

        metrics.setRunningThreadCount(running);
        metrics.setWaitingThreadCount(waiting);
        metrics.setBlockedThreadCount(blocked);

        log.debug("Thread pool metrics updated - active: {}, pool: {}, queue: {}, completed: {}, " +
                "running: {}, waiting: {}, blocked: {}",
            metrics.getActivePoolThreads(), metrics.getPoolSize(),
            metrics.getQueuedTasks(), metrics.getCompletedTasks(),
            metrics.getRunningThreadCount(), metrics.getWaitingThreadCount(),
            metrics.getBlockedThreadCount());
    }

    private void updateThreadStates(ThreadMetrics metrics) {
        if (metrics.getThreadId() <= 0) {
            log.warn("Invalid thread ID in metrics: {}", metrics.getThreadId());
            return;
        }

        ThreadInfo threadInfo = threadMXBean.getThreadInfo(metrics.getThreadId());
        if (threadInfo != null) {
            metrics.setThreadState(threadInfo.getThreadState());
            if (metrics.getChildThreads() != null) {
                updateChildThreadStates(metrics);
            }
        }
    }

    private void updateChildThreadStates(ThreadMetrics metrics) {
        if (metrics.getChildThreads() != null) {
            for (ThreadMetrics childMetric : metrics.getChildThreads().values()) {
                ThreadInfo childInfo = threadMXBean.getThreadInfo(childMetric.getThreadId());
                if (childInfo != null) {
                    childMetric.setThreadState(childInfo.getThreadState());
                }
            }
        }
    }

    private void updateCpuTimes(ThreadMetrics metrics) {
        if (metrics.getThreadId() <= 0) {
            return;
        }

        try {
            long id = metrics.getThreadId();
            metrics.setThreadCpuTime(threadMXBean.getThreadCpuTime(id));
            metrics.setThreadUserTime(threadMXBean.getThreadUserTime(id));

            if (metrics.getChildThreads() != null) {
                updateChildThreadCpuTimes(metrics);
            }
        } catch (Exception e) {
            log.error("Error updating CPU times: {}", e.getMessage());
        }
    }

    private void updateChildThreadCpuTimes(ThreadMetrics metrics) {
        if (metrics.getChildThreads() != null) {
            for (ThreadMetrics childMetric : metrics.getChildThreads().values()) {
                if (childMetric.getThreadId() > 0) {
                    long childId = childMetric.getThreadId();
                    childMetric.setThreadCpuTime(threadMXBean.getThreadCpuTime(childId));
                    childMetric.setThreadUserTime(threadMXBean.getThreadUserTime(childId));
                }
            }
        }
    }

    public ThreadMetrics getMethodMetrics(String className, String methodName) {
        String key = className + "." + methodName;
        ThreadMetrics metrics = methodMetrics.get(key);
        if (metrics != null) {
            updateMetrics(metrics);
        }
        return metrics;
    }

    public void stopMethodMonitoring(String className, String methodName) {
        String key = className + "." + methodName;
        ThreadMetrics metrics = methodMetrics.remove(key);
        if (metrics != null) {
            threadToMethod.remove(metrics.getThreadId());
            if (metrics.getChildThreads() != null) {
                metrics.getChildThreads().keySet()
                    .forEach(threadToMethod::remove);
            }
        }
    }

    public void registerChildThread(long parentThreadId, Thread childThread) {
        String methodKey = threadToMethod.get(parentThreadId);
        if (methodKey != null) {
            ThreadMetrics parentMetrics = methodMetrics.get(methodKey);
            if (parentMetrics != null) {
                ThreadMetrics childMetrics = ThreadMetrics.builder()
                    .type(ThreadMetrics.MetricType.CHILD_THREAD)
                    .threadId(childThread.getId())
                    .threadName(childThread.getName())
                    .parentThreadId(parentThreadId)
                    .isDaemon(childThread.isDaemon())
                    .priority(childThread.getPriority())
                    .threadState(childThread.getState())
                    .childThreads(new ConcurrentHashMap<>())
                    .build();

                parentMetrics.addChildThread(childMetrics);
                threadToMethod.put(childThread.getId(), methodKey);

                log.debug("Registered child thread: {} (ID: {}) for parent thread: {} (ID: {})",
                    childThread.getName(), childThread.getId(),
                    Thread.currentThread().getName(), parentThreadId);
            }
        }
    }
}