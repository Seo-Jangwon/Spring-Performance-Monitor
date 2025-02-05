/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */


package com.monitor.annotation.thread;

import com.monitor.annotation.service.ThreadMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadFactory;

@Component
@RequiredArgsConstructor
public class MonitoredThreadFactory implements ThreadFactory {

    private final ThreadMonitorService threadMonitorService;

    @Override
    public Thread newThread(Runnable r) {
        Thread parentThread = Thread.currentThread();
        Thread thread = new Thread(r);
        // 새로 생성된 스레드를 현재 실행 중인 메서드의 자식 스레드로 등록
        threadMonitorService.registerChildThread(parentThread.getId(), thread);
        return thread;
    }
}