/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

const { Subject, BehaviorSubject } = window.rxjs;
const { filter, map, bufferTime, share, distinctUntilChanged } = window.rxjs.operators;
import {metricsService} from './MetricsSSEService.js';

class RxMetricsService {
  constructor() {
    this.activeTestId$ = new BehaviorSubject(null);
    this.rawMetrics$ = new Subject();

    this.metrics$ = this.rawMetrics$.pipe(
        filter(data => data.testId === this.activeTestId$.value),
        bufferTime(100),
        filter(buffer => buffer.length > 0),
        map(buffer => buffer[buffer.length - 1]),
        share()
    );

    this.status$ = this.metrics$.pipe(
        map(data => data.testStatus),
        filter(status => !!status),
        distinctUntilChanged(
            (prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
        share()
    );

    this.threadMetrics$ = this.metrics$.pipe(
        map(data => data.threadMetrics),
        filter(metrics => !!metrics),
        distinctUntilChanged(
            (prev, curr) => JSON.stringify(prev) === JSON.stringify(curr)),
        share()
    );
  }

  startMonitoring(testId) {
    console.log('[RxMetrics] Starting monitoring for test:', testId);
    this.activeTestId$.next(testId);
    const metricsStream = metricsService.connect(testId);
    if (metricsStream) {
      metricsStream.subscribe({
        next: data => {
          console.log('[RxMetrics] Received data:', data);
          this.rawMetrics$.next(data);
        },
        error: error => console.error('[RxMetrics] Error:', error)
      });
    }
  }

  stopMonitoring() {
    console.log('[RxMetrics] Stopping monitoring');
    metricsService.cleanup();
    this.activeTestId$.next(null);
  }
}

export const rxMetricsService = new RxMetricsService();