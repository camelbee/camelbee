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

  // Bug found 2026-08-15: selecting an endpoint (consumer/producer) node used to swap its
  // background to a fixed light blue while its text stayed white (sized for the node's own
  // dark/colored background) - unreadable. Selection must add a ring, never replace the
  // node's own background/text-color pairing.
  it('keeps an endpoint node readable (own background + white text) when selected', () => {
    renderNode({ label: 'sel-endpoint', componentType: 'kafka', kind: 'consumer' }, true);
    const card = screen.getByText('sel-endpoint').closest('[title]')!;
    expect(card.className).toContain('bg-orange-500'); // kafka's nodeBg (colorMap.ts)
    expect(card.className).toContain('ring-blue-500');
    expect(card.className).not.toContain('bg-blue-100');
    const label = screen.getByText('sel-endpoint');
    expect(label.className).toContain('text-white');
  });

  // Roadmap #1+15 (route descriptions): richer hover tooltip. Lives on the whole node card
  // (not just the truncated label text) so hovering anywhere on the node shows it - not only
  // the exact label line.
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
    expect(el.closest('[title]')).toHaveAttribute(
      'title',
      'Route: orderRoute\nDescription: Handles orders\nInput: direct:orders\nError handler: direct:dlq\nREST endpoint',
    );
  });

  it('falls back to the label as the tooltip when no extra metadata is present', () => {
    renderNode({ label: 'orders-in', componentType: 'kafka', kind: 'consumer' });
    expect(screen.getByText('orders-in').closest('[title]')).toHaveAttribute('title', 'orders-in');
  });

  // Bug found 2026-08-15: a producer (external endpoint) node's label is truncated for display,
  // but the tooltip fell back to that same truncated label - no full-URI field existed at all.
  it('shows the full untruncated URI on hover for a producer node', () => {
    renderNode({
      label: 'http://localhost:8080/api/he…',
      componentType: 'http',
      kind: 'producer',
      fullUri: 'http://localhost:8080/api/health?bridgeEndpoint=true',
    });
    const el = screen.getByText('http://localhost:8080/api/he…');
    expect(el.closest('[title]')).toHaveAttribute(
      'title',
      'Endpoint: http://localhost:8080/api/health?bridgeEndpoint=true',
    );
  });
});
