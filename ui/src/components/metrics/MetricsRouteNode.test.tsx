import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MetricsRouteNode, type MetricsRouteNodeData } from './MetricsRouteNode';
import { FlowWrapper, nodeProps } from '@/test/flowWrapper';

function renderNode(data: MetricsRouteNodeData) {
  return render(<MetricsRouteNode {...nodeProps<MetricsRouteNodeData>(data)} />, {
    wrapper: FlowWrapper,
  });
}

describe('MetricsRouteNode', () => {
  it('shows succeeded/failed counts when there are metrics', () => {
    renderNode({
      label: 'orders',
      componentType: 'kafka',
      kind: 'consumer',
      exchangesTotal: 10,
      exchangesFailed: 3,
    });
    expect(screen.getByText('orders')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument(); // total - failed
    expect(screen.getByText('3')).toBeInTheDocument(); // failed badge
  });

  it('renders a node with no metrics (gray traffic light, no badges)', () => {
    renderNode({ label: 'idle', componentType: 'direct', kind: 'internal' });
    expect(screen.getByText('idle')).toBeInTheDocument();
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('renders an all-success node without a failure badge', () => {
    renderNode({
      label: 'ok',
      componentType: 'timer',
      kind: 'consumer',
      exchangesTotal: 5,
      exchangesFailed: 0,
    });
    expect(screen.getByText('5')).toBeInTheDocument();
  });

  it('renders a producer node (no traffic light)', () => {
    renderNode({ label: 'http://out', componentType: 'http', kind: 'producer' });
    expect(screen.getByText('http://out')).toBeInTheDocument();
  });
});
