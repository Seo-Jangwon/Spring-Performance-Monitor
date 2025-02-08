/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

package com.monitor.annotation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for RestTemplate setup.
 * Configures RestTemplate with specific timeout settings and connection pool parameters.
 *
 * Settings:
 * - Connect timeout: 5000ms
 * - Read timeout: 5000ms
 * - Max concurrent connections: 200
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates and configures a RestTemplate bean with custom timeout and connection settings.
     *
     * @return Configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "200"); // Set N of concurrent connection

        return new RestTemplate(factory);
    }
}