/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

import {chartService} from '../core/ChartService.js';
import {metricsService} from '../core/MetricsSSEService.js';
import {
  formatBytes,
  formatDuration,
  calculateDuration,
  formatNumber
} from '../utils/formatters.js';

/**
 * Manages the display and monitoring of performance test details.
 * Handles real-time metrics updates, historical data processing, and chart visualization.
 * 성능 테스트 상세 정보 표시와 모니터링 관리
 * 실시간 메트릭 업데이트, 과거 데이터 처리, 차트 시각화 처리
 */
class TestDetailsManager {

  /**
   * Initializes a new TestDetailsManager instance.
   * Creates necessary state variables for tracking test details and metrics.
   * 새로운 TestDetailsManager 인스턴스 초기화
   * 테스트 상세 정보 및 메트릭을 추적하는 데 필요한 상태 변수들 생성
   */
  constructor() {
    this.currentTestId = null;
    this.modalInstance = null;
    this.metricsSubscription = null;
    this.testResults = new Map();
    this.isChartInitialized = false;
    this.processedDataPoints = new Set();
  }

  /**
   * Displays test details for a specific test ID.
   * Handles both completed tests (historical data) and ongoing tests (real-time monitoring).
   * 특정 테스트 ID에 대한 테스트 상세 정보 표시
   * 완료된 테스트(과거 데이터), 진행 중인 테스트(실시간 모니터링) 모두 처리
   *
   * @param {string} testId - Unique identifier for the test
   */
  async showDetails(testId) {
    try {
      console.log('[TestDetails] Loading details for test:', testId);

      if (this.currentTestId === testId) {
        console.log('[TestDetails] Already showing this test');
        return;
      }

      this.currentTestId = testId;
      const data = await this.fetchTestData(testId);

      await this.initializeModal();
      await this.initializeCharts();

      if (data.completed) {
        console.log('[TestDetails] Processing historical data');
        await this.processHistoricalData(data);
      } else {
        console.log('[TestDetails] Setting up real-time monitoring');
        this.setupRealtimeMonitoring(data);
      }
    } catch (error) {
      console.error('[TestDetails] Error showing details:', error);
    }
  }

  /**
   * Fetches test data from the server for a specific test ID.
   * Retrieves both test status and metrics data.
   * 특정 테스트 ID에 대한 테스트 데이터를 서버에서 가져옴
   * 테스트 상태와 메트릭 데이터를 모두 조회
   *
   * @param {string} testId - Unique identifier for the test
   * @returns {Promise<Object>} Test data including status and metrics
   * @throws {Error} If the server request fails
   */
  async fetchTestData(testId) {
    const response = await fetch(`/performanceMeasure/status/${testId}`);
    if (!response.ok) {
      throw new Error('Failed to load test data');
    }
    const data = await response.json();
    this.testResults.set(testId, data);
    return data;
  }

  /**
   * Initializes the Bootstrap modal for displaying test details.
   * Sets up event listeners and handles modal lifecycle.
   * 테스트 상세 정보를 표시하기 위한 Bootstrap 모달 초기화
   * 이벤트 리스너를 설정하고 모달 생명주기 관리
   *
   * @returns {Promise<void>} Resolves when modal is initialized
   */
  async initializeModal() {
    return new Promise((resolve) => {
      const modalElement = document.getElementById('detailsModal');
      if (!modalElement) {
        resolve();
        return;
      }

      if (this.modalInstance) {
        this.modalInstance.dispose();
      }

      this.modalInstance = new bootstrap.Modal(modalElement);

      modalElement.addEventListener('hidden.bs.modal', () => {
        this.cleanup();
      });

      this.modalInstance.show();
      resolve();
    });
  }

  /**
   * Initializes Chart.js instances for displaying metrics.
   * Creates charts for response time, memory usage, and thread metrics.
   * 메트릭을 표시하기 위한 Chart.js 인스턴스들 초기화
   * 응답 시간, 메모리 사용량, 스레드 메트릭을 위한 차트들 생성
   *
   * @returns {Promise<void>} Resolves when charts are initialized
   */
  async initializeCharts() {
    await new Promise(resolve => setTimeout(resolve, 100));
    this.isChartInitialized = false;
    chartService.initializeCharts();
    this.isChartInitialized = true;
  }

  /**
   * Processes and displays historical test data.
   * Replays metrics data chronologically with visualization.
   * 과거 테스트 데이터를 처리하고 표시
   * 메트릭 데이터를 시간순으로 재생하며 시각화
   *
   * @param {Object} data - Complete test data including metrics history
   */
  async processHistoricalData(data) {
    if (!data.memoryMetrics?.length) {
      console.log('[TestDetails] No historical metrics found');
      return;
    }

    console.log('[TestDetails] Processing historical metrics:', {
      metricsCount: data.memoryMetrics.length,
      responseTimesCount: data.responseTimes?.length
    });

    this.updateMetricsDisplay({
      testStatus: data,
      metrics: data.memoryMetrics[0],
      threadMetrics: data.threadMetrics
    });

    for (const metric of data.memoryMetrics) {
      const dataKey = `${metric.timestamp}`;
      if (this.processedDataPoints.has(dataKey)) {
        continue;
      }
      this.processedDataPoints.add(dataKey);

      const currentIndex = data.memoryMetrics.indexOf(metric);
      const currentResponseTimes = data.responseTimes?.slice(0,
          currentIndex + 1);

      await this.updateMetricsDisplay({
        metrics: metric,
        testStatus: {
          ...data,
          responseTimes: currentResponseTimes
        },
        threadMetrics: data.threadMetrics
      });

      await new Promise(resolve => setTimeout(resolve, 50));
    }
  }

  /**
   * Sets up real-time monitoring for ongoing tests.
   * Establishes SSE connection and handles incoming metrics updates.
   * 진행 중인 테스트에 대한 실시간 모니터링 설정
   * SSE 연결을 설정하고 들어오는 메트릭 업데이트 처리
   *
   * @param {Object} data - Initial test data for monitoring setup
   */
  setupRealtimeMonitoring(data) {
    try {
      console.log('[TestDetails] Setting up real-time monitoring');

      // 초기 데이터 표시
      this.updateMetricsDisplay({
        testStatus: data,
        metrics: data.memoryMetrics?.[0],
        threadMetrics: data.threadMetrics
      });

      // 기존 구독이 있다면 정리
      if (this.metricsSubscription) {
        this.metricsSubscription.unsubscribe();
        this.metricsSubscription = null;
      }

      // SSE 연결 전에 상태 확인
      if (!this.currentTestId) {
        console.warn('[TestDetails] No active test ID for monitoring');
        return;
      }

      // SSE 연결 시도
      const messageStream = metricsService.connect(this.currentTestId);
      if (!messageStream) {
        console.error('[TestDetails] Failed to establish metrics stream');
        return;
      }

      // 새로운 구독 설정
      this.metricsSubscription = messageStream.subscribe({
        next: (data) => {
          if (!this.currentTestId) return; // 모달이 닫힌 경우 처리 중지

          console.log('[TestDetails] Received realtime update');
          const dataKey = `${data.metrics?.timestamp}`;
          if (!this.processedDataPoints.has(dataKey)) {
            this.processedDataPoints.add(dataKey);
            this.updateMetricsDisplay(data);
          }
        },
        error: (error) => {
          console.error('[TestDetails] Metrics stream error:', error);
          // 에러 발생 시 UI에 표시
          this.updateElement('test-status', 'Error: Connection lost');
        },
        complete: () => {
          console.log('[TestDetails] Metrics stream completed');
        }
      });
    } catch (error) {
      console.error('[TestDetails] Error in setupRealtimeMonitoring:', error);
    }
  }

  /**
   * Updates the metrics display in the UI.
   * Handles updates for test status, memory metrics, and thread metrics.
   * UI의 메트릭 표시를 업데이트
   * 테스트 상태, 메모리 메트릭, 스레드 메트릭 업데이트를 처리
   *
   * @param {Object} data - Current metrics data to display
   */
  updateMetricsDisplay(data) {
    if (!this.isChartInitialized || !data) {
      return;
    }

    console.log('[TestDetails] Updating metrics display');

    // Test Status Updates
    if (data.testStatus) {
      this.updateElement('modal-description', data.testStatus.description);
      this.updateElement('modal-url', data.testStatus.url);
      this.updateElement('modal-method', data.testStatus.method);
      this.updateElement('modal-duration',
          calculateDuration(data.testStatus.startTime, new Date()));
      this.updateElement('modal-total-requests',
          formatNumber(data.testStatus.totalRequests));
      this.updateElement('modal-success-rate',
          `${(100 - (data.testStatus.errorRate || 0)).toFixed(2)}%`);
      this.updateElement('modal-avg-response',
          `${formatNumber(data.testStatus.averageResponseTime)} ms`);
      this.updateElement('modal-rps',
          formatNumber(data.testStatus.requestsPerSecond));
    }

    // Memory Metrics Updates
    if (data.metrics) {
      this.updateMemoryMetrics(data.metrics);
    }

    // Thread Metrics Updates
    if (data.threadMetrics) {
      this.updateThreadMetrics(data.threadMetrics);
    }

    // Chart Updates
    chartService.updateCharts(data);
  }

  /**
   * Updates memory-related metrics in the UI.
   * Displays heap, non-heap, GC, and thread pool metrics.
   * UI의 메모리 관련 메트릭 업데이트
   * 힙, 논힙, GC, 스레드 풀 메트릭 표시
   *
   * @param {Object} metrics - Memory metrics data
   */
  updateMemoryMetrics(metrics) {
    // Heap Memory
    this.updateElement('modal-heap-used', formatBytes(metrics.heapUsed));
    this.updateElement('modal-heap-max', formatBytes(metrics.heapMax));
    this.updateElement('modal-young-gen', formatBytes(metrics.youngGenUsed));
    this.updateElement('modal-old-gen', formatBytes(metrics.oldGenUsed));

    // Non-Heap Memory
    this.updateElement('modal-nonheap-used', formatBytes(metrics.nonHeapUsed));
    this.updateElement('modal-nonheap-committed',
        formatBytes(metrics.nonHeapCommitted));
    this.updateElement('modal-metaspace-used',
        formatBytes(metrics.metaspaceUsed));

    // Thread Pool Status
    if (metrics.performanceThreadPool) {
      const pool = metrics.performanceThreadPool;
      this.updateElement('modal-active-threads', pool.activeThreads);
      this.updateElement('modal-pool-size', pool.poolSize);
      this.updateElement('modal-max-pool-size', pool.maxPoolSize);
      this.updateElement('modal-queue-size', pool.queueSize);
      this.updateElement('modal-running-threads', pool.runningThreads);
      this.updateElement('modal-waiting-threads', pool.waitingThreads);
      this.updateElement('modal-blocked-threads', pool.blockedThreads);
      this.updateElement('modal-total-threads', metrics.threadCount);
    }

    // GC Metrics
    this.updateElement('modal-young-gc-count', metrics.youngGcCount);
    this.updateElement('modal-old-gc-count', metrics.oldGcCount);
    this.updateElement('modal-gc-time',
        `${metrics.youngGcTime + metrics.oldGcTime} ms`);
  }

  /**
   * Updates thread-related metrics in the UI.
   * Displays thread state, CPU time, and other thread details.
   * UI의 스레드 관련 메트릭 업데이트
   * 스레드 상태, CPU 시간, 기타 스레드 상세 정보 표시
   *
   * @param {Object} threadMetrics - Thread metrics data
   */
  updateThreadMetrics(threadMetrics) {
    this.updateElement('method-thread-name', threadMetrics.threadName || '-');
    this.updateElement('method-thread-id', threadMetrics.threadId || '-');
    this.updateElement('method-cpu-time',
        formatDuration(threadMetrics.threadCpuTime));
    this.updateElement('method-user-time',
        formatDuration(threadMetrics.threadUserTime));
    this.updateElement('method-thread-state', threadMetrics.threadState || '-');
    this.updateElement('method-thread-priority', threadMetrics.priority || '-');
    this.updateElement('method-thread-daemon',
        threadMetrics.isDaemon != null ?
            (threadMetrics.isDaemon ? 'Yes' : 'No') : '-');
  }

  /**
   * Updates a single DOM element with new value.
   * Handles null values with default display.
   * 단일 DOM 엘리먼트를 새로운 값으로 업데이트
   * null 값을 기본 표시로 처리
   *
   * @param {string} id - DOM element ID to update
   * @param {*} value - New value to display
   */
  updateElement(id, value) {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = value ?? '-';
    }
  }

  /**
   * Cleans up resources when closing test details.
   * Handles subscription cleanup and chart destruction.
   * 테스트 상세 정보를 닫을 때 리소스 정리
   * 구독 정리와 차트 제거 처리
   */
  cleanup() {
    console.log('[TestDetails] Cleaning up all resources');

    if (this.metricsSubscription) {
      this.metricsSubscription.unsubscribe();
      this.metricsSubscription = null;
    }

    // 차트 인스턴스 정리
    chartService.destroyCharts();
    this.isChartInitialized = false;
    this.currentTestId = null;
  }
}

export const testDetailsManager = new TestDetailsManager();