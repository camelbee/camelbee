import { useQuery } from '@tanstack/react-query';
import type { PrometheusMetric } from '../types';
import { apiFetchText } from './client';

/**
 * Parse Prometheus exposition format into name/value pairs.
 * Lines starting with # are comments and are skipped.
 */
export function parsePrometheus(text: string): PrometheusMetric[] {
  const metrics: PrometheusMetric[] = [];
  for (const line of text.split('\n')) {
    if (!line || line.startsWith('#')) continue;
    // Prometheus line: metric_name{labels} value  OR  metric_name value
    const parts = line.includes('}')
      ? line.split('} ')
      : line.split(' ');
    if (parts.length < 2) continue;
    const name = (parts[0] ?? '') + (line.includes('}') ? '}' : '');
    const value = parseFloat(parts[1] ?? '');
    if (!isNaN(value)) {
      metrics.push({ name, value });
    }
  }
  return metrics;
}

/** Fetch Prometheus metrics from configurable URL */
function fetchMetrics(url: string): Promise<PrometheusMetric[]> {
  return apiFetchText(url).then(parsePrometheus).catch(() => []);
}

export function useMetrics(enabled: boolean, refreshRateSeconds = 5, url = '/metrics') {
  return useQuery({
    queryKey: ['metrics', url],
    queryFn: () => fetchMetrics(url),
    enabled,
    refetchInterval: enabled ? refreshRateSeconds * 1000 : false,
    retry: false,
  });
}
