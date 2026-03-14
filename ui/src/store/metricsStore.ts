import { create } from 'zustand';
import type { PrometheusMetric } from '@/types';

export interface TimeSeriesPoint {
  timestamp: number;
  value: number;
}

export interface RouteMetrics {
  routeId: string;
  exchangesTotal: number;
  exchangesFailed: number;
  exchangesSucceeded: number;
}

interface MetricsState {
  /* Time series data (keyed by metric name) */
  cpuUsage: TimeSeriesPoint[];
  processCpu: TimeSeriesPoint[];
  memoryUsed: TimeSeriesPoint[];
  memoryMax: TimeSeriesPoint[];
  gcPauseCount: TimeSeriesPoint[];
  gcPauseSum: TimeSeriesPoint[];
  threadsLive: TimeSeriesPoint[];
  threadsDaemon: TimeSeriesPoint[];
  threadsPeak: TimeSeriesPoint[];

  /* Per-route metrics */
  routeMetrics: RouteMetrics[];

  /* Raw metrics text for detail modal */
  rawMetrics: PrometheusMetric[];

  /* Max history (seconds) */
  maxHistory: number;

  /* Actions */
  updateFromPrometheus: (metrics: PrometheusMetric[]) => void;
  setMaxHistory: (seconds: number) => void;
  clear: () => void;
}

function findMetric(metrics: PrometheusMetric[], pattern: string): number | null {
  const m = metrics.find((m) => m.name.includes(pattern));
  return m ? m.value : null;
}

function sumMetrics(metrics: PrometheusMetric[], pattern: string, areaFilter?: string): number {
  return metrics
    .filter((m) => m.name.includes(pattern) && (!areaFilter || m.name.includes(areaFilter)))
    .reduce((sum, m) => sum + m.value, 0);
}

function trimSeries(series: TimeSeriesPoint[], maxHistory: number): TimeSeriesPoint[] {
  const cutoff = Date.now() - maxHistory * 1000;
  return series.filter((p) => p.timestamp > cutoff);
}

function appendPoint(series: TimeSeriesPoint[], value: number | null, maxHistory: number): TimeSeriesPoint[] {
  if (value === null) return series;
  const trimmed = trimSeries(series, maxHistory);
  return [...trimmed, { timestamp: Date.now(), value }];
}

export const useMetricsStore = create<MetricsState>((set, get) => ({
  cpuUsage: [],
  processCpu: [],
  memoryUsed: [],
  memoryMax: [],
  gcPauseCount: [],
  gcPauseSum: [],
  threadsLive: [],
  threadsDaemon: [],
  threadsPeak: [],
  routeMetrics: [],
  rawMetrics: [],
  maxHistory: 300,

  updateFromPrometheus: (metrics) => {
    const state = get();
    const mh = state.maxHistory;

    // CPU
    const cpuVal = findMetric(metrics, 'system_cpu_usage');
    const procCpuVal = findMetric(metrics, 'process_cpu_usage');

    // Memory (heap used/max)
    const heapUsed = sumMetrics(metrics, 'jvm_memory_used_bytes', 'area="heap"');
    const heapMax = sumMetrics(metrics, 'jvm_memory_max_bytes', 'area="heap"');

    // GC
    const gcCount = sumMetrics(metrics, 'jvm_gc_pause_seconds_count');
    const gcSum = sumMetrics(metrics, 'jvm_gc_pause_seconds_sum');

    // Threads
    const live = findMetric(metrics, 'jvm_threads_live_threads');
    const daemon = findMetric(metrics, 'jvm_threads_daemon_threads');
    const peak = findMetric(metrics, 'jvm_threads_peak_threads');

    // Route metrics
    const routeMap = new Map<string, RouteMetrics>();
    for (const m of metrics) {
      const routeMatch = m.name.match(/routeId="([^"]+)"/);
      if (!routeMatch) continue;
      const routeId = routeMatch[1]!;
      if (!routeMap.has(routeId)) {
        routeMap.set(routeId, { routeId, exchangesTotal: 0, exchangesFailed: 0, exchangesSucceeded: 0 });
      }
      const entry = routeMap.get(routeId)!;
      if (m.name.startsWith('camel_exchanges_total{')) {
        entry.exchangesTotal = m.value;
      } else if (m.name.startsWith('camel_exchanges_failed_total{')) {
        entry.exchangesFailed = m.value;
      } else if (m.name.startsWith('camel_exchanges_succeeded_total{')) {
        entry.exchangesSucceeded = m.value;
      }
    }

    set({
      cpuUsage: appendPoint(state.cpuUsage, cpuVal !== null ? cpuVal * 100 : null, mh),
      processCpu: appendPoint(state.processCpu, procCpuVal !== null ? procCpuVal * 100 : null, mh),
      memoryUsed: appendPoint(state.memoryUsed, heapUsed > 0 ? heapUsed / (1024 * 1024) : null, mh),
      memoryMax: appendPoint(state.memoryMax, heapMax > 0 ? heapMax / (1024 * 1024) : null, mh),
      gcPauseCount: appendPoint(state.gcPauseCount, gcCount > 0 ? gcCount : null, mh),
      gcPauseSum: appendPoint(state.gcPauseSum, gcSum > 0 ? gcSum * 1000 : null, mh),
      threadsLive: appendPoint(state.threadsLive, live, mh),
      threadsDaemon: appendPoint(state.threadsDaemon, daemon, mh),
      threadsPeak: appendPoint(state.threadsPeak, peak, mh),
      routeMetrics: Array.from(routeMap.values()),
      rawMetrics: metrics,
    });
  },

  setMaxHistory: (seconds) => set({ maxHistory: seconds }),

  clear: () =>
    set({
      cpuUsage: [],
      processCpu: [],
      memoryUsed: [],
      memoryMax: [],
      gcPauseCount: [],
      gcPauseSum: [],
      threadsLive: [],
      threadsDaemon: [],
      threadsPeak: [],
      routeMetrics: [],
      rawMetrics: [],
    }),
}));
