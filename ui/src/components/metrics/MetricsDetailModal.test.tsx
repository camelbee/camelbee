import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MetricsDetailModal } from './MetricsDetailModal';
import type { PrometheusMetric } from '@/types';

const metrics: PrometheusMetric[] = [
  { name: 'system_cpu_usage', value: 0.5 },
  { name: 'jvm_threads_live_threads', value: 42 },
];

describe('MetricsDetailModal', () => {
  it('lists all metrics as name/value lines', () => {
    render(<MetricsDetailModal metrics={metrics} onClose={() => {}} />);
    expect(screen.getByText(/system_cpu_usage 0.5/)).toBeInTheDocument();
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
  });

  it('filters metrics by the search box', () => {
    render(<MetricsDetailModal metrics={metrics} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText('Search...'), { target: { value: 'cpu' } });
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    expect(screen.getByText(/system_cpu_usage 0.5/)).toBeInTheDocument();
  });

  it('shows a fallback when nothing matches', () => {
    render(<MetricsDetailModal metrics={metrics} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText('Search...'), { target: { value: 'zzz' } });
    expect(screen.getByText('No metrics available.')).toBeInTheDocument();
  });

  it('invokes onClose when the close button is clicked', () => {
    const onClose = vi.fn();
    render(<MetricsDetailModal metrics={metrics} onClose={onClose} />);
    fireEvent.click(screen.getByText('✕'));
    expect(onClose).toHaveBeenCalledOnce();
  });
});
