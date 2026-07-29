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

  // Roadmap #1+15 (route descriptions): richer hover tooltip.
  it('builds a multi-line tooltip from routeId/description/inputUri/errorHandler/isRest', () => {
    renderNode({
      label: 'Handles orders',
      componentType: 'direct',
      kind: 'consumer',
      routeId: 'orderRoute',
      description: 'Handles orders',
      inputUri: 'direct:orders',
      errorHandler: 'direct:dlq',
      isRest: true,
    });
    const el = screen.getByText('Handles orders');
    expect(el).toHaveAttribute(
      'title',
      'Route: orderRoute\nDescription: Handles orders\nInput: direct:orders\nError handler: direct:dlq\nREST endpoint',
    );
  });

  it('falls back to the label as the tooltip when no extra metadata is present', () => {
    renderNode({ label: 'orders-in', componentType: 'kafka', kind: 'consumer' });
    expect(screen.getByText('orders-in')).toHaveAttribute('title', 'orders-in');
  });
});
