import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RouteNode } from './RouteNode';
import type { RouteNodeData } from '@/utils/routeGraph';
import { FlowWrapper, nodeProps } from '@/test/flowWrapper';

function renderNode(data: RouteNodeData, selected = false) {
  return render(<RouteNode {...nodeProps<RouteNodeData>(data, { selected })} />, {
    wrapper: FlowWrapper,
  });
}

describe('RouteNode', () => {
  it('renders a consumer node with its component type and label', () => {
    renderNode({ label: 'orders-in', componentType: 'kafka', kind: 'consumer' });
    expect(screen.getByText('orders-in')).toBeInTheDocument();
    expect(screen.getByText('kafka')).toBeInTheDocument();
  });

  it('renders a producer node', () => {
    renderNode({ label: 'http://api', componentType: 'http', kind: 'producer' });
    expect(screen.getByText('http://api')).toBeInTheDocument();
  });

  it('renders an internal node', () => {
    renderNode({ label: 'step', componentType: 'direct', kind: 'internal' });
    expect(screen.getByText('step')).toBeInTheDocument();
  });

  it('renders an error node', () => {
    renderNode({ label: 'handler', componentType: 'error', kind: 'error' });
    expect(screen.getByText('handler')).toBeInTheDocument();
  });

  it('applies selected styling without throwing', () => {
    renderNode({ label: 'sel', componentType: 'direct', kind: 'internal' }, true);
    expect(screen.getByText('sel')).toBeInTheDocument();
  });
});
