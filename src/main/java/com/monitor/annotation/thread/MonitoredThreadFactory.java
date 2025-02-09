/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.thread;

import com.monitor.annotation.service.ThreadMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadFactory;

/**
 * Custom ThreadFactory that creates monitored threads for performance testing.
 * Automatically registers newly created threads with the ThreadMonitorService,
 * enabling parent-child thread relationship tracking and comprehensive thread metrics collection.
 *
 * This factory is crucial for:
 * - Maintaining thread hierarchy information
 * - Enabling detailed thread monitoring
 * - Correlating thread activities with their parent methods
 */
@Component
@RequiredArgsConstructor
public class MonitoredThreadFactory implements ThreadFactory {

    private final ThreadMonitorService threadMonitorService;

    /**
     * Creates a new thread and registers it with the monitoring service.
     * The new thread is automatically linked to its parent thread in the monitoring system,
     * enabling hierarchical thread tracking and metrics collection.
     *
     * @param r The Runnable to be executed by the new thread
     * @return A new Thread instance that's registered with the monitoring service
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread parentThread = Thread.currentThread();
        Thread thread = new Thread(r);
        // Registers the newly created thread as a child thread of the currently executing method.
        threadMonitorService.registerChildThread(parentThread.getId(), thread);
        return thread;
    }
}