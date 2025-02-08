/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

import {testDetailsManager} from '../test-details.js';

/**
 * WebSocketManager for handling real-time metrics streaming.
 * Manages WebSocket connection lifecycle and data processing for live updates of:
 * - Memory metrics
 * - Thread metrics
 * - Test execution status
 */
class WebSocketManager {
  constructor() {
    this.ws = null;
  }

  /**
   * Establishes WebSocket connection for a specific test.
   * Handles connection lifecycle events and data processing.
   * @param {string} testId - ID of the test to monitor
   */
  connectWebSocket(testId) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      console.log('Closing existing WebSocket connection');
      this.ws.close();
    }

    try {
      console.log('Connecting to WebSocket...');
      this.ws = new WebSocket(`ws://${window.location.host}/ws/metrics`);

      this.ws.onopen = () => {
        console.log('WebSocket connected, sending testId:', testId);
        if (this.ws.readyState === WebSocket.OPEN) {
          this.ws.send(testId);
        }
      };

      /**
       * Handles incoming WebSocket messages.
       * Processes different types of metrics and updates UI accordingly.
       */
      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          console.log('WebSocket received data:', data);

          // Track last known thread metrics
          let lastThreadMetrics = null;

          // Process memory metrics
          if (data.metrics) {
            testDetailsManager.updateMemoryMetrics({
              ...data.metrics,
              performanceThreadPool: {
                activeThreads: data.metrics.performanceThreadPool?.activeThreads
                    || 0,
                poolSize: data.metrics.performanceThreadPool?.poolSize || 0,
                maxPoolSize: data.metrics.performanceThreadPool?.maxPoolSize
                    || 0,
                queueSize: data.metrics.performanceThreadPool?.queueSize || 0,
                runningThreads: data.metrics.performanceThreadPool?.runningThreads
                    || 0,
                waitingThreads: data.metrics.performanceThreadPool?.waitingThreads
                    || 0,
                blockedThreads: data.metrics.performanceThreadPool?.blockedThreads
                    || 0
              },
              threadCount: data.metrics.threadCount
            });
          }

          if (data.threadMetrics) {
            testDetailsManager.updateMethodThreadMetrics({
              ...data.threadMetrics,
              threadCpuTime: data.threadMetrics.threadCpuTime || 0,
              threadUserTime: data.threadMetrics.threadUserTime || 0,
              daemon: data.threadMetrics.daemon != null
                  ? data.threadMetrics.daemon : false
            });
          }

          if (data.testStatus) {
            testDetailsManager.updateTestStatus(data.testStatus);

            // 테스트 완료시에도 마지막 스레드 메트릭 사용
            if (data.testStatus.completed && lastThreadMetrics) {
              testDetailsManager.updateMethodThreadMetrics(lastThreadMetrics);
            }
          }

          if (data.metrics) {
            testDetailsManager.updateMemoryMetrics(data.metrics);
            testDetailsManager.updateChartsWithNewData(data.metrics,
                data.testStatus);
          }

          // Handle test completion
          if (data.testStatus && data.testStatus.completed) {
            const finalState = {
              metrics: data.metrics,
              threadMetrics: lastThreadMetrics || data.threadMetrics,
              testStatus: data.testStatus
            };
            localStorage.setItem(`test_${data.testStatus.testId}`,
                JSON.stringify(finalState));
          }

        } catch (error) {
          console.error('Error handling WebSocket message:', error);
        }
      };

      /**
       * Handles WebSocket errors and connection closure.
       * Implements automatic reconnection strategy.
       */
      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error);
      };

      this.ws.onclose = (event) => {
        console.log('WebSocket closed:', event);
        if (!event.wasClean) {
          console.log('Attempting to reconnect...');
          setTimeout(() => {
            this.connectWebSocket(testId);
          }, 3000);
        }
      };

    } catch (error) {
      console.error('Error creating WebSocket:', error);
    }
  }
}

export const webSocketManager = new WebSocketManager();