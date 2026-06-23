import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { cloneElement, type ReactElement } from 'react';
import { MetricsCharts } from './MetricsCharts';
import { useMetricsStore, type TimeSeriesPoint } from '@/store/metricsStore';

// ResponsiveContainer measures its parent, which is 0x0 in jsdom and would
// render nothing. Replace it with the chart sized explicitly (the real
// container injects width/height into its child the same way).
vi.mock('recharts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('recharts')>();
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: ReactElement<Record<string, unknown>> }) =>
      cloneElement(children, { width: 400, height: 200 }),
  };
});

function series(values: number[]): TimeSeriesPoint[] {
  const now = Date.now();
  return values.map((value, i) => ({ timestamp: now + i * 1000, value }));
}

describe('MetricsCharts', () => {
  beforeEach(() => {
    useMetricsStore.getState().clear();
  });

  it('shows the "waiting" placeholders when there is no data', () => {
    render(<MetricsCharts />);
    expect(screen.getByText('CPU Usage')).toBeInTheDocument();
    expect(screen.getAllByText('Waiting for data...').length).toBeGreaterThan(0);
  });

  it('renders recharts line charts once the store has time-series data', () => {
    useMetricsStore.setState({
      cpuUsage: series([10, 20, 30]),
      processCpu: series([5, 6, 7]),
      memoryUsed: series([100, 110, 120]),
      memoryMax: series([512, 512, 512]),
      gcPauseSum: series([1, 2, 3]),
      threadsLive: series([20, 21, 22]),
      threadsDaemon: series([10, 10, 11]),
      threadsPeak: series([25, 26, 26]),
    });

    const { container } = render(<MetricsCharts />);

    // All four panels are present...
    expect(screen.getByText('CPU Usage')).toBeInTheDocument();
    expect(screen.getByText('GC Average Pauses')).toBeInTheDocument();
    expect(screen.getByText('JVM Memory Usage')).toBeInTheDocument();
    expect(screen.getByText('Threads')).toBeInTheDocument();

    // ...and no longer waiting for data.
    expect(screen.queryByText('Waiting for data...')).not.toBeInTheDocument();

    // recharts mounted and produced SVG output (catches API breakage on upgrade).
    expect(container.querySelectorAll('svg.recharts-surface').length).toBeGreaterThan(0);
  });
});
