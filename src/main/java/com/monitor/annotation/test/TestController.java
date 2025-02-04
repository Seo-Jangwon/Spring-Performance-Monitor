/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.test;

import com.monitor.annotation.annotation.PerformanceMeasure;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @PerformanceMeasure("간단한 API 테스트")
    @GetMapping("/simple")
    public String simpleTest() {
        return "Hello World";
    }

    @PerformanceMeasure("시간이 걸리는 API 테스트")
    @GetMapping("/slow")
    public String slowTest() throws InterruptedException {
        Thread.sleep(1000); // 1초 대기
        return "Slow Response";
    }

    @PerformanceMeasure("메모리를 많이 사용하는 API 테스트")
    @GetMapping("/memory")
    public String memoryTest() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            sb.append("test data ");
        }
        return "Memory Test Complete";
    }
}