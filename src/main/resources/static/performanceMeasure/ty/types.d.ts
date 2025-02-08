/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

interface Endpoint {
  endpointUrl: string;
  controllerClassName: string;
  controllerMethodName: string;
  requestType?: string;
  requestExample?: Object;
  description?: string;
  httpMethod?: string;
  annotatedServices?: Array<{
    serviceClassName: string;
    methodName: string;
    description?: string;
  }>;
}

interface StatusResponse {
  testId: string;                 // 테스트 고유 ID
  status: string;                 // 테스트 상태
  description?: string;           // 테스트 설명
  url?: string;                   // 테스트 대상 URL
  method?: string;                // HTTP 메서드 (GET, POST 등)
  startTime?: Date;               // 테스트 시작 시간
  averageResponseTime: number;    // 평균 응답 시간 (ms)
  maxResponseTime: number;        // 최대 응답 시간 (ms)
  requestsPerSecond: number;      // 초당 요청 수
  errorRate: number;              // 에러율 (%)
  completed: boolean;             // 테스트 완료 여부
  totalRequests?: number;         // 총 요청 수
  responseTimes?: number[];       // 응답 시간 배열
  endpointUrl?: string;          // 엔드포인트 URL
  requestType?: string;          // 요청 타입
  httpMethod?: string;           // HTTP 메서드
  controllerClassName?: string;   // 컨트롤러 클래스명
  controllerMethodName?: string;  // 컨트롤러 메서드명
  annotatedServices?: Array<{     // 관련 서비스 정보
    serviceClassName: string;
    methodName: string;
    description?: string;
  }>;
}

interface ThreadMetrics {
  threadName?: string;
  threadId?: string;
  threadCpuTime?: number;
  threadUserTime?: number;
  threadState?: string;
  priority?: number;
  isDaemon?: boolean;
  stackTrace?: Array<{
    className: string;
    methodName: string;
    fileName: string;
    lineNumber: number;
  }>;
}

interface TestResult {
  memoryMetrics?: MemoryMetrics[];
  responseTimes?: number[];
  completed?: boolean;
}

interface MemoryMetrics {
  timestamp: number;
  heapUsed: number;
  heapMax: number;
  youngGenUsed: number;
  oldGenUsed: number;
  nonHeapUsed: number;
  nonHeapCommitted: number;
  metaspaceUsed: number;
  youngGcCount: number;
  oldGcCount: number;
  youngGcTime: number;
  oldGcTime: number;
  threadCount: number;
  performanceThreadPool: ThreadPoolMetrics;
}

interface ThreadPoolMetrics {
  activeThreads: number;
  queueSize: number;
  poolSize: number;
  maxPoolSize: number;
  runningThreads: number;
  waitingThreads: number;
  blockedThreads: number;
}

interface TestResult {
  description?: string;
  url?: string;
  method?: string;
  startTime?: Date;
  totalRequests: number;
  errorRate: number;
  averageResponseTime: number;
  requestsPerSecond: number;
  metrics?: MemoryMetrics;
  daemon?: string;
  threadMetrics?: ThreadMetrics;
}

interface BootstrapModal {
  show(): void;
  hide(): void;
  dispose(): void;
}

interface BootstrapStatic {
  Modal: {
    new(element: HTMLElement | null, options?: any): BootstrapModal;
  };
}

interface Window {
  bootstrap: BootstrapStatic;
}