/*
 * Copyright (c) 2025 Seo-Jangwon
 * Licensed under MIT License
 */

import {formatTime} from './formatters.js';
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
  Legend,
  LinearScale,
  CategoryScale
}
  from 'https://cdn.jsdelivr.net/npm/chart.js@4.4.2/+esm';

Chart.register(LineController, LineElement, PointElement, Tooltip, Legend,
    LinearScale, CategoryScale);

/**
 * ChartManager class for managing real-time performance metric visualizations.
 * Handles initialization and updates of multiple charts:
 * - Memory usage (heap, young gen, old gen)
 * - Non-heap memory usage
 * - Thread pool status
 * - Response time metrics
 */
class ChartManager {
  constructor() {
    this.responseTimeChart = null;
    this.memoryUsageChart = null;
    this.nonHeapChart = null;
    this.threadChart = null;
  }

  /**
   * Initializes all chart instances with default configurations.
   * Destroys existing charts if they exist before creating new ones.
   */
  initializeCharts() {
    if (this.memoryUsageChart) {
      this.memoryUsageChart.destroy();
    }
    if (this.responseTimeChart) {
      this.responseTimeChart.destroy();
    }
    if (this.nonHeapChart) {
      this.nonHeapChart.destroy();
    }
    if (this.threadChart) {
      this.threadChart.destroy();
    }

    const memoryCtx = document.getElementById('memoryChart').getContext('2d');
    const responseCtx = document.getElementById('responseTimeChart').getContext(
        '2d');
    const nonHeapCtx = document.getElementById('nonHeapChart').getContext('2d');
    const threadCtx = document.getElementById('threadChart').getContext('2d');

    // Memory Chart
    this.memoryUsageChart = new Chart(memoryCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Heap Usage',
            borderColor: 'rgb(54, 162, 235)',
            data: []
          },
          {
            label: 'Young Gen',
            borderColor: 'rgb(255, 99, 132)',
            data: []
          },
          {
            label: 'Old Gen',
            borderColor: 'rgb(75, 192, 192)',
            data: []
          }
        ]
      },
      options: this.createChartOptions('Memory Usage (MB)')
    });

    // Non-Heap Memory Chart
    this.nonHeapChart = new Chart(nonHeapCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Non-Heap Used',
            borderColor: 'rgb(153, 102, 255)',
            data: []
          },
          {
            label: 'Metaspace Used',
            borderColor: 'rgb(255, 159, 64)',
            data: []
          }
        ]
      },
      options: this.createChartOptions('Non-Heap Memory (MB)')
    });

    // Thread Chart
    this.threadChart = new Chart(threadCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: 'Active Threads',
            borderColor: 'rgb(75, 192, 192)',
            data: []
          },
          {
            label: 'Queue Size',
            borderColor: 'rgb(255, 205, 86)',
            data: []
          }
        ]
      },
      options: this.createChartOptions('Thread Pool Status')
    });

    // Response Time Chart
    this.responseTimeChart = new Chart(responseCtx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: 'Response Time (ms)',
          borderColor: 'rgb(75, 192, 192)',
          data: []
        }]
      },
      options: this.createChartOptions('Response Time (ms)')
    });
  }

  /**
   * Creates standard chart options configuration.
   * @param {string} yAxisLabel - Label for the Y axis
   * @returns {Object} Chart.js options configuration
   */
  createChartOptions(yAxisLabel) {
    return {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        y: {
          beginAtZero: true,
          title: {
            display: true,
            text: yAxisLabel
          }
        }
      },
      animation: {
        duration: 0
      }
    };
  }

  /**
   * Updates all charts with historical data.
   * Used when loading saved test results or initializing charts with existing data.
   * @param {Object} chartData - Historical chart data including labels and datasets
   */
  updateAllChartsWithHistory(chartData) {
    // Updates memory chart with historical data
    this.memoryUsageChart.data = {
      labels: chartData.labels,
      datasets: [
        {
          label: 'Heap Usage',
          data: chartData.memoryData.heap,
          borderColor: 'rgb(54, 162, 235)'
        },
        {
          label: 'Young Gen',
          data: chartData.memoryData.young,
          borderColor: 'rgb(255, 99, 132)'
        },
        {
          label: 'Old Gen',
          data: chartData.memoryData.old,
          borderColor: 'rgb(75, 192, 192)'
        }
      ]
    };
    this.memoryUsageChart.update();

    // 논힙 메모리 차트
    this.nonHeapChart.data = {
      labels: chartData.labels,
      datasets: [
        {
          label: 'Non-Heap Used',
          data: chartData.nonHeapData.nonHeap,
          borderColor: 'rgb(153, 102, 255)'
        },
        {
          label: 'Metaspace Used',
          data: chartData.nonHeapData.metaspace,
          borderColor: 'rgb(255, 159, 64)'
        }
      ]
    };
    this.nonHeapChart.update();

    // 스레드 풀 차트
    this.threadChart.data = {
      labels: chartData.labels,
      datasets: [
        {
          label: 'Active Threads',
          data: chartData.threadPoolData.active,
          borderColor: 'rgb(75, 192, 192)'
        },
        {
          label: 'Queue Size',
          data: chartData.threadPoolData.queue,
          borderColor: 'rgb(255, 205, 86)'
        },
        {
          label: 'Pool Size',
          data: chartData.threadPoolData.pool,
          borderColor: 'rgb(153, 102, 255)'
        }
      ]
    };
    this.threadChart.update();

    // 응답 시간 차트
    this.responseTimeChart.data = {
      labels: chartData.responseTimeData.map((_, i) => i + 1),
      datasets: [{
        label: 'Response Time (ms)',
        data: chartData.responseTimeData,
        borderColor: 'rgb(75, 192, 192)',
        tension: 0.1
      }]
    };
    this.responseTimeChart.update();
  }

  /**
   * Updates heap memory chart with new metrics.
   * Maintains a sliding window of the last 50 data points.
   * @param {Object} metrics - Current memory metrics
   */
  updateHeapMemoryChart(metrics) {
    const timestamp = formatTime(metrics.timestamp);

    if (this.memoryUsageChart.data.labels.length > 50) {
      this.memoryUsageChart.data.labels.shift();
      this.memoryUsageChart.data.datasets.forEach(
          dataset => dataset.data.shift());
    }

    this.memoryUsageChart.data.labels.push(timestamp);
    this.memoryUsageChart.data.datasets[0].data.push(
        metrics.heapUsed / (1024 * 1024));
    this.memoryUsageChart.data.datasets[1].data.push(
        metrics.youngGenUsed / (1024 * 1024));
    this.memoryUsageChart.data.datasets[2].data.push(
        metrics.oldGenUsed / (1024 * 1024));

    this.memoryUsageChart.update();
  }

  /**
   * Updates non-heap memory chart with new metrics.
   * Includes Metaspace and other non-heap memory areas.
   * @param {Object} metrics - Current memory metrics
   */
  updateNonHeapMemoryChart(metrics) {
    const timestamp = formatTime(metrics.timestamp);

    if (this.nonHeapChart.data.labels.length > 50) {
      this.nonHeapChart.data.labels.shift();
      this.nonHeapChart.data.datasets.forEach(dataset => dataset.data.shift());
    }

    this.nonHeapChart.data.labels.push(timestamp);
    this.nonHeapChart.data.datasets[0].data.push(
        metrics.nonHeapUsed / (1024 * 1024));
    this.nonHeapChart.data.datasets[1].data.push(
        metrics.metaspaceUsed / (1024 * 1024));

    this.nonHeapChart.update();
  }

  /**
   * Updates thread pool chart with current thread metrics.
   * Shows active threads, queue size, and total pool size.
   * @param {Object} metrics - Current thread pool metrics
   */
  updateThreadPoolChart(metrics) {
    const timestamp = formatTime(metrics.timestamp);
    const pool = metrics.performanceThreadPool;

    if (!pool) {
      return;
    }

    if (this.threadChart.data.labels.length > 50) {
      this.threadChart.data.labels.shift();
      this.threadChart.data.datasets.forEach(dataset => dataset.data.shift());
    }

    this.threadChart.data.labels.push(timestamp);
    this.threadChart.data.datasets[0].data.push(pool.activeThreads);
    this.threadChart.data.datasets[1].data.push(pool.queueSize);

    // 새로운 데이터셋
    if (this.threadChart.data.datasets.length === 2) {
      this.threadChart.data.datasets.push({
        label: 'Pool Size',
        borderColor: 'rgb(153, 102, 255)',
        data: [pool.poolSize]
      });
    } else {
      this.threadChart.data.datasets[2].data.push(pool.poolSize);
    }

    this.threadChart.update();
  }

  /**
   * Updates response time chart with latest test execution data.
   * @param {Object} testStatus - Current test status including response times
   */
  updateResponseTimeChart(testStatus) {
    this.responseTimeChart.data = {
      labels: testStatus.responseTimes.map((_, i) => i + 1),
      datasets: [{
        label: 'Response Time (ms)',
        data: testStatus.responseTimes,
        borderColor: 'rgb(75, 192, 192)',
        tension: 0.1
      }]
    };

    this.responseTimeChart.update();
  }

}

export const chartManager = new ChartManager();