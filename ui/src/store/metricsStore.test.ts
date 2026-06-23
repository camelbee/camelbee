import { describe, it, expect, beforeEach } from 'vitest';
import { useMetricsStore } from './metricsStore';
import type { PrometheusMetric } from '@/types';

const get = () => useMetricsStore.getState();

const sample: PrometheusMetric[] = [
  { name: 'system_cpu_usage', value: 0.5 },
  { name: 'process_cpu_usage', value: 0.25 },
  { name: 'jvm_memory_used_bytes{area="heap",id="eden"}', value: 1024 * 1024 * 10 },
  { name: 'jvm_memory_used_bytes{area="heap",id="old"}', value: 1024 * 1024 * 20 },
  { name: 'jvm_memory_max_bytes{area="heap",id="eden"}', value: 1024 * 1024 * 512 },
  { name: 'jvm_gc_pause_seconds_count', value: 3 },
  { name: 'jvm_gc_pause_seconds_sum', value: 0.12 },
  { name: 'jvm_threads_live_threads', value: 42 },
  { name: 'jvm_threads_daemon_threads', value: 10 },
  { name: 'jvm_threads_peak_threads', value: 50 },
  { name: 'camel_exchanges_total{routeId="r1"}', value: 100 },
  { name: 'camel_exchanges_failed_total{routeId="r1"}', value: 4 },
  { name: 'camel_exchanges_succeeded_total{routeId="r1"}', value: 96 },
];

describe('metricsStore', () => {
  beforeEach(() => {
    get().clear();
    get().setMaxHistory(300);
  });

  it('derives time-series points from Prometheus metrics', () => {
    get().updateFromPrometheus(sample);
    const s = get();
    const last = (arr: { value: number }[]) => arr[arr.length - 1]!.value;

    expect(last(s.cpuUsage)).toBe(50); // 0.5 * 100
    expect(last(s.processCpu)).toBe(25);
    expect(last(s.memoryUsed)).toBe(30); // (10+20) MB summed across heap areas
    expect(last(s.memoryMax)).toBe(512);
    expect(last(s.gcPauseSum)).toBeCloseTo(120); // 0.12s -> 120ms
    expect(last(s.threadsLive)).toBe(42);
    expect(last(s.threadsPeak)).toBe(50);
  });

  it('aggregates per-route exchange counters', () => {
    get().updateFromPrometheus(sample);
    const route = get().routeMetrics.find((r) => r.routeId === 'r1')!;
    expect(route).toMatchObject({ exchangesTotal: 100, exchangesFailed: 4, exchangesSucceeded: 96 });
  });

  it('accumulates points across successive updates', () => {
    get().updateFromPrometheus(sample);
    get().updateFromPrometheus(sample);
    expect(get().cpuUsage.length).toBe(2);
    expect(get().rawMetrics.length).toBe(sample.length);
  });

  it('does not append a point when a metric is absent', () => {
    get().updateFromPrometheus([{ name: 'system_cpu_usage', value: 0.1 }]);
    const s = get();
    expect(s.cpuUsage).toHaveLength(1);
    expect(s.threadsLive).toHaveLength(0); // no thread metric present
  });

  it('clear() empties all series', () => {
    get().updateFromPrometheus(sample);
    get().clear();
    const s = get();
    expect(s.cpuUsage).toHaveLength(0);
    expect(s.routeMetrics).toHaveLength(0);
    expect(s.rawMetrics).toHaveLength(0);
  });

  it('setMaxHistory updates the retention window', () => {
    get().setMaxHistory(600);
    expect(get().maxHistory).toBe(600);
  });
});
