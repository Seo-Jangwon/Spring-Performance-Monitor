/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

const {Subject} = window.rxjs;

/**
 * Service for managing Server-Sent Events (SSE) connections for metrics streaming.
 * Handles real-time metrics data flow and connection lifecycle.
 * SSE 연결을 관리하는 서비스
 * 메트릭 스트리밍을 처리
 * 실시간 메트릭 데이터 흐름 및 연결 생명주기 관리
 */
class MetricsSSEService {

  /**
   * Initializes service with default configuration for SSE connections.
   * SSE 연결을 위한 기본 설정으로 서비스 초기화
   */
  constructor() {
    this.eventSource = null;
    this.messages$ = new Subject();
    this.reconnectAttempts = 0;
    this.MAX_RECONNECT_ATTEMPTS = 5;
    this.RECONNECT_DELAY = 1000;
    this.currentTestId = null;
    this.isConnectionActive = false;
    this.reconnectTimeoutId = null;
  }

  /**
   * Establishes SSE connection for a specific test.
   * Returns existing connection if already established for the same test.
   * 특정 테스트에 대한 SSE 연결 수립
   * 동일한 테스트에 대해 이미 연결이 있다면 기존 연결 반환
   *
   * @param {string} testId - Unique identifier for the test
   * @returns {Subject|null} RxJS Subject for message streaming or null if connection fails
   */
  connect(testId) {
    if (!testId) {
      console.error('[SSE] TestID is required');
      return null;
    }

    if (this.isConnectionActive && this.currentTestId === testId) {
      console.log('[SSE] Reusing existing connection');
      return this.messages$;
    }

    this.currentTestId = testId;
    this.establishConnection();
    return this.messages$;
  }

  /**
   * Creates new SSE connection and sets up event handlers.
   * Manages connection state and error handling.
   * 새로운 SSE 연결 생성 및 이벤트 핸들러 설정
   * 연결 상태 관리 및 에러 처리
   */
  establishConnection() {
    if (this.eventSource?.readyState === 1) { // OPEN
      console.log('[SSE] Connection already established');
      return;
    }

    const url = `/performanceMeasure/metrics/stream/${this.currentTestId}`;

    try {
      if (this.eventSource) {
        this.eventSource.close();
        this.eventSource = null;
      }

      console.log('[SSE] Establishing new connection...');
      this.eventSource = new EventSource(url);

      this.eventSource.onopen = () => {
        console.log('[SSE] Connection established successfully');
        this.isConnectionActive = true;
        this.reconnectAttempts = 0;
      };

      this.eventSource.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          this.messages$.next(data);

          if (data.testStatus?.completed) {
            console.log('[SSE] Test completed, closing connection');
            this.cleanup(true);
          }
        } catch (error) {
          console.error('[SSE] Error processing message:', error);
        }
      };

      this.eventSource.onerror = (error) => {
        const state = this.eventSource?.readyState;
        console.error(`[SSE] Connection error (state: ${state}):`, error);

        // Call close only if it is not already in CLOSED
        if (state !== 2) {
          this.eventSource?.close();
        }

        this.isConnectionActive = false;

        // Attempt to reconnect only if normal test completion does not occur
        if (!this.isTestCompleted) {
          this.handleReconnection();
        }
      };

    } catch (error) {
      console.error('[SSE] Failed to establish connection:', error);
      this.handleReconnection();
    }
  }

  /**
   * Handles reconnection attempts using exponential backoff.
   * Manages reconnection timing and maximum retry attempts.
   * 지수 백오프를 사용한 재연결 시도 처리
   * 재연결 타이밍과 최대 재시도 횟수 관리
   */
  handleReconnection() {
    // Prevent duplicate attempts if reconnection is already in progress
    if (this.reconnectTimeoutId) {
      return;
    }

    if (this.reconnectAttempts < this.MAX_RECONNECT_ATTEMPTS) {
      const delay = this.RECONNECT_DELAY * Math.pow(2, this.reconnectAttempts);
      console.log(
          `[SSE] Attempting to reconnect in ${delay}ms (attempt ${this.reconnectAttempts
          + 1}/${this.MAX_RECONNECT_ATTEMPTS})`);

      this.reconnectTimeoutId = setTimeout(() => {
        this.reconnectAttempts++;
        this.establishConnection();
        this.reconnectTimeoutId = null;
      }, delay);
    } else {
      console.error('[SSE] Max reconnection attempts reached');
      this.cleanup();
      this.messages$.error(
          new Error('Connection failed after maximum retries'));
    }
  }

  /**
   * Cleans up SSE connection and resets service state.
   * Handles both normal and error closure scenarios.
   * SSE 연결 정리 및 서비스 상태 초기화
   * 정상 종료와 에러 종료 시나리오 모두 처리
   *
   * @param {boolean} isNormalClosure - Whether the cleanup is due to normal test completion
   */
  cleanup(isNormalClosure = false) {
    if (this.reconnectTimeoutId) {
      clearTimeout(this.reconnectTimeoutId);
      this.reconnectTimeoutId = null;
    }

    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    this.isConnectionActive = false;
    this.currentTestId = null;
    this.reconnectAttempts = 0;
    this.isTestCompleted = isNormalClosure;

    if (isNormalClosure) {
      console.log('[SSE] Connection closed normally');
    }
  }
}

export const metricsService = new MetricsSSEService();