/** A single data point in a metric time-series */
export interface MetricValue {
  value: number;
  timestamp: number;
}

/** A named metric with its accumulated time-series values */
export interface Metric {
  name: string;
  values: MetricValue[];
}

/** Parsed Prometheus metric line */
export interface PrometheusMetric {
  name: string;
  value: number;
}
