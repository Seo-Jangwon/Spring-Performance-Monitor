/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

import {
  formatBytes,
  formatDuration,
  calculateDuration
} from './utils/formatters.js';
import {chartManager} from './utils/charts.js';
import {webSocketManager} from './utils/websocket.js'
import {mainManager} from './main.js'
import {formatTime} from './utils/formatters.js';

/**
 * TestDetailsManager class for handling detailed test result visualization.
 * Provides functionality for:
 * - Real-time metrics updates
 * - Chart data management
 * - Stack trace visualization
 * - Detailed test status display
 */
class TestDetailsManager {
  constructor() {
    this.currentStackTrace = null;
  }

  /**
   * Prepares chart data from test results.
   * Processes raw metrics into format suitable for visualization.
   * @param {Object} testResult - Raw test result data
   * @returns {Object} Formatted chart data
   */
  prepareChartData(testResult) {
    if (!testResult.memoryMetrics || !Array.isArray(testResult.memoryMetrics)) {
      return {
        labels: [],
        memoryData: {heap: [], young: [], old: []},
        nonHeapData: {nonHeap: [], metaspace: []},
        threadPoolData: {active: [], queue: [], pool: []},
        responseTimeData: testResult.responseTimes || []
      };
    }

    return {
      labels: testResult.memoryMetrics.map(m => formatTime(m.timestamp)),
      memoryData: {
        heap: testResult.memoryMetrics.map(
            m => (m.heapUsed || 0) / (1024 * 1024)),
        young: testResult.memoryMetrics.map(
            m => (m.youngGenUsed || 0) / (1024 * 1024)),
        old: testResult.memoryMetrics.map(
            m => (m.oldGenUsed || 0) / (1024 * 1024))
      },
      nonHeapData: {
        nonHeap: testResult.memoryMetrics.map(
            m => (m.nonHeapUsed || 0) / (1024 * 1024)),
        metaspace: testResult.memoryMetrics.map(
            m => (m.metaspaceUsed || 0) / (1024 * 1024))
      },
      threadPoolData: {
        active: testResult.memoryMetrics.map(
            m => m.performanceThreadPool?.activeThreads || 0),
        queue: testResult.memoryMetrics.map(
            m => m.performanceThreadPool?.queueSize || 0),
        pool: testResult.memoryMetrics.map(
            m => m.performanceThreadPool?.poolSize || 0)
      },
      responseTimeData: testResult.responseTimes || []
    };
  }

  /**
   * Updates test status display with current metrics.
   * @param {Object} testStatus - Current test status and metrics
   */
  updateTestStatus(testStatus) {
    console.log('Updating test status with:', testStatus);
    if (!testStatus) {
      console.warn('Test status is null or undefined');
      return;
    }

    try {
      // 기본 정보 업데이트
      this.updateElement('modal-description', testStatus.description);
      this.updateElement('modal-url', testStatus.url);
      this.updateElement('modal-method', testStatus.method);
      this.updateElement('modal-duration',
          calculateDuration(testStatus.startTime, new Date()));

      // 테스트 결과 업데이트
      this.updateElement('modal-total-requests', testStatus.totalRequests || 0);
      this.updateElement('modal-success-rate',
          `${(100 - (testStatus.errorRate || 0)).toFixed(2)}%`);
      this.updateElement('modal-avg-response',
          `${(testStatus.averageResponseTime || 0).toFixed(2)} ms`);
      this.updateElement('modal-rps',
          (testStatus.requestsPerSecond || 0).toFixed(2));

      console.log('Test status updated successfully');
    } catch (error) {
      console.error('Error updating test status:', error);
    }

    if (testStatus.responseTimes && testStatus.responseTimes.length > 0) {
      chartManager.responseTimeChart.data = {
        labels: testStatus.responseTimes.map((_, i) => i + 1),
        datasets: [{
          label: 'Response Time (ms)',
          data: testStatus.responseTimes,
          borderColor: 'rgb(75, 192, 192)',
          tension: 0.1
        }]
      };
      chartManager.responseTimeChart.update('none');  // 애니메이션 없이 즉시 업데이트
    }
  }

  /**
   * Updates all charts with new metric data.
   * @param {Object} metrics - Current metrics data
   * @param {Object} testStatus - Current test status
   */
  updateChartsWithNewData(metrics, testStatus) {
    if (!metrics || !testStatus) {
      return;
    }

    // if (testStatus.completed) {
    //   return;
    // }

    chartManager.updateHeapMemoryChart(metrics);
    chartManager.updateNonHeapMemoryChart(metrics);
    chartManager.updateThreadPoolChart(metrics);
    chartManager.updateResponseTimeChart(testStatus);
  }

  /**
   * Updates specific element content by ID
   */
  updateElement(id, value) {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = value;
    }
  }

  /**
   * Updates modal content with test result data
   */
  updateModalContent(result) {
    if (!result) {
      console.warn('No result data provided to updateModalContent');
      return;
    }

    // 기본 정보 업데이트
    this.updateElement('modal-description', result.description);
    this.updateElement('modal-url', result.url);
    this.updateElement('modal-method', result.method);
    this.updateElement('modal-duration',
        calculateDuration(result.startTime, new Date()));

    // 테스트 결과 업데이트
    this.updateElement('modal-total-requests', result.totalRequests || 0);
    this.updateElement('modal-success-rate',
        `${(100 - (result.errorRate || 0)).toFixed(2)}%`);
    this.updateElement('modal-avg-response',
        `${(result.averageResponseTime || 0).toFixed(2)} ms`);
    this.updateElement('modal-rps', (result.requestsPerSecond || 0).toFixed(2));

    // 메모리 메트릭 업데이트
    this.updateElement('modal-heap-used',
        formatBytes(result.metrics?.heapUsed || 0));
    this.updateElement('modal-heap-max',
        formatBytes(result.metrics?.heapMax || 0));
    this.updateElement('modal-young-gen',
        formatBytes(result.metrics?.youngGenUsed || 0));
    this.updateElement('modal-old-gen',
        formatBytes(result.metrics?.oldGenUsed || 0));

    this.updateElement('modal-nonheap-used',
        formatBytes(result.metrics?.nonHeapUsed || 0));
    this.updateElement('modal-nonheap-committed',
        formatBytes(result.metrics?.nonHeapCommitted || 0));
    this.updateElement('modal-metaspace-used',
        formatBytes(result.metrics?.metaspaceUsed || 0));

    this.updateElement('modal-young-gc-count',
        result.metrics?.youngGcCount || 0);
    this.updateElement('modal-old-gc-count', result.metrics?.oldGcCount || 0);
    this.updateElement('modal-gc-time',
        `${result.metrics?.youngGcTime + result.metrics?.oldGcTime || 0} ms`);

    // 스레드 상태 업데이트
    this.updateElement('modal-active-threads',
        result.metrics?.activeThreads || 0);
    this.updateElement('modal-pool-size', result.metrics?.poolSize || 0);
    this.updateElement('modal-max-pool-size', result.metrics?.maxPoolSize || 0);
    this.updateElement('modal-queue-size', result.metrics?.queueSize || 0);

    // 스레드 상태 카운트 업데이트
    this.updateElement('modal-running-threads',
        result.metrics?.runningThreads || 0);
    this.updateElement('modal-waiting-threads',
        result.metrics?.waitingThreads || 0);
    this.updateElement('modal-blocked-threads',
        result.metrics?.blockedThreads || 0);
    this.updateElement('modal-total-threads', result.metrics?.threadCount || 0);

    // 메서드 스레드 메트릭 업데이트
    if (result.threadMetrics) {
      this.updateMethodThreadMetrics(result.threadMetrics);
    }
  }

  /**
   * Updates memory metrics display with current values
   */
  updateMemoryMetrics(metrics) {
    if (!metrics) {
      //   // metrics가 없으면 저장된 마지막 메트릭을 사용
      //   const savedMetrics = localStorage.getItem('lastMemoryMetrics');
      //   if (savedMetrics) {
      //     metrics = JSON.parse(savedMetrics);
      //   } else {
      //     return; // 저장된 메트릭도 없으면 종료
      //   }
      // } else {
      //   // 새로운 메트릭이 들어오면 저장
      //   const currentMetrics = {
      //     timestamp: new Date().getTime(),
      //     ...metrics
      //   };
      //   localStorage.setItem('lastMemoryMetrics', JSON.stringify(currentMetrics));
      return;
    }

    // 힙 메모리 업데이트
    this.updateElement('modal-heap-used', formatBytes(metrics.heapUsed));
    this.updateElement('modal-heap-max', formatBytes(metrics.heapMax));
    this.updateElement('modal-young-gen', formatBytes(metrics.youngGenUsed));
    this.updateElement('modal-old-gen', formatBytes(metrics.oldGenUsed));

    // 논힙 메모리 업데이트
    this.updateElement('modal-nonheap-used', formatBytes(metrics.nonHeapUsed));
    this.updateElement('modal-nonheap-committed',
        formatBytes(metrics.nonHeapCommitted));
    this.updateElement('modal-metaspace-used',
        formatBytes(metrics.metaspaceUsed));

    // GC 메트릭 업데이트
    this.updateElement('modal-young-gc-count', metrics.youngGcCount);
    this.updateElement('modal-old-gc-count', metrics.oldGcCount);
    this.updateElement('modal-gc-time',
        `${metrics.youngGcTime + metrics.oldGcTime} ms`);

    // Thread Pool 메트릭 업데이트
    const pool = metrics.performanceThreadPool;
    if (pool) {
      this.updateElement('modal-active-threads', pool.activeThreads);
      this.updateElement('modal-pool-size', `${pool.poolSize}`);
      this.updateElement('modal-max-pool-size', pool.maxPoolSize);
      this.updateElement('modal-queue-size', pool.queueSize);

      // 스레드 상태 업데이트
      this.updateElement('modal-running-threads', pool.runningThreads);
      this.updateElement('modal-waiting-threads', pool.waitingThreads);
      this.updateElement('modal-blocked-threads', pool.blockedThreads);
      this.updateElement('modal-total-threads', metrics.threadCount);
    }
  }

  /**
   * Updates method thread metrics display
   */
  updateMethodThreadMetrics(threadMetrics) {
    console.log('Updating thread metrics:', threadMetrics);
    if (!threadMetrics) {
      this.clearMethodThreadMetrics();
      return;
    }

    // Thread 정보 업데이트
    this.updateElement('method-thread-name', threadMetrics.threadName || '-');
    this.updateElement('method-thread-id', threadMetrics.threadId || '-');
    this.updateElement('method-cpu-time',
        threadMetrics.threadCpuTime != null ? formatDuration(
            threadMetrics.threadCpuTime) : '-');
    this.updateElement('method-user-time',
        threadMetrics.threadUserTime != null ? formatDuration(
            threadMetrics.threadUserTime) : '-');
    this.updateElement('method-thread-state', threadMetrics.threadState || '-');
    this.updateElement('method-thread-priority', threadMetrics.priority || '-');
    this.updateElement('method-thread-daemon',
        threadMetrics.daemon != null ? (threadMetrics.daemon ? 'Yes' : 'No')
            : '-');

    this.currentStackTrace = threadMetrics.stackTrace || null;
  }

  /**
   * Clears all method thread metrics display
   */
  clearMethodThreadMetrics() {
    this.updateElement('method-thread-name', '-');
    this.updateElement('method-thread-id', '-');
    this.updateElement('method-cpu-time', '-');
    this.updateElement('method-user-time', '-');
    this.updateElement('method-thread-state', '-');
    this.updateElement('method-thread-priority', '-');
    this.updateElement('method-thread-daemon', '-');
    this.currentStackTrace = null;
  }

  /**
   * Shows stack trace information in a modal.
   * Formats and displays current thread stack trace.
   */
  showStackTrace() {
    const stackTraceContent = document.getElementById('stack-trace-content');
    if (!this.currentStackTrace || !Array.isArray(this.currentStackTrace)) {
      stackTraceContent.textContent = 'No stack trace available';
    } else {
      const formattedTrace = this.currentStackTrace
      .map(
          frame => `    at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})`)
      .join('\n');
      stackTraceContent.textContent = formattedTrace || 'Empty stack trace';
    }

    const modal = new bootstrap.Modal(
        document.getElementById('stackTraceModal'));
    modal.show();
  }

  /**
   * Shows detailed test information in a modal.
   * Initializes charts and starts WebSocket connection for live updates.
   * @param {string} testId - ID of the test to display
   */
  showDetails(testId) {
    const modalElement = document.getElementById('detailsModal');
    if (!modalElement) {
      console.error('Modal element not found');
      return;
    }

    const bsModal = new window.bootstrap.Modal(modalElement);
    bsModal.show();

    // 모달이 표시된 후의 동작
    modalElement.addEventListener('shown.bs.modal', () => {
      fetch(`/performanceMeasure/status/${testId}`)
      .then(response => response.json())
      .then(result => {
        this.updateModalContent(result);

        chartManager.initializeCharts();

        if (result.completed) {
          const savedState = localStorage.getItem(`test_${testId}`);
          if (savedState) {
            const state = JSON.parse(savedState);
            if (state.metrics) {
              this.updateMemoryMetrics(state.metrics);
            }
            if (state.threadMetrics) {
              this.updateMethodThreadMetrics(state.threadMetrics);
            }
          }

          if (result.memoryMetrics && result.memoryMetrics.length > 0) {
            const chartData = this.prepareChartData(result);
            chartManager.updateAllChartsWithHistory(chartData);
          }
        } else {
          webSocketManager.connectWebSocket(testId);
        }
        const modalElement = document.getElementById('detailsModal');
        if (modalElement) {
          // 기존 모달이 있다면 제거
          const existingModal = bootstrap.Modal.getInstance(modalElement);
          if (existingModal) {
            existingModal.dispose();
            // backdrop 강제 제거
            document.querySelector('.modal-backdrop')?.remove();
            document.body.classList.remove('modal-open');
            document.body.style.overflow = '';
            document.body.style.paddingRight = '';
          }

          // 새 모달 생성 및 표시
          const bsModal = new window.bootstrap.Modal(modalElement);

          // 모달 닫힐 때 정리
          modalElement.addEventListener('hidden.bs.modal', () => {
            const existingModal = bootstrap.Modal.getInstance(modalElement);
            if (existingModal) {
              existingModal.dispose();
            }

            document.querySelector('.modal-backdrop')?.remove();
            document.body.classList.remove('modal-open');
            document.body.style.overflow = '';
            document.body.style.paddingRight = '';
          });

          bsModal.show();
        }
      })
      .catch(error => {
        console.error('Error:', error);
        mainManager.showError('Failed to load test details');
      });
    }, {once: true});
  }
}

export const testDetailsManager = new TestDetailsManager();