/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 연결 타임아웃 설정
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        // Keep-Alive 설정
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "200"); // 동시 연결 수 설정

        return new RestTemplate(factory);
    }
}