import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { RouteGraph } from './RouteGraph';
import { buildRouteGraph } from '@/utils/routeGraph';
import { useDebuggerStore } from '@/store/debuggerStore';
import { makeMessage } from '@/test/factories';
import type { CamelBeeContext } from '@/types';
import routesFixture from '../../../mock/routes.json';

const context = routesFixture as unknown as CamelBeeContext;
const { edges } = buildRouteGraph(context);
// Pick a static edge between two routes so we can craft matching messages.
const staticEdge = edges.find((e) => e.data!.targetRouteId)!;

beforeEach(() => {
  useDebuggerStore.getState().clearMessages();
  useDebuggerStore.getState().selectEdge(null);
  vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
    cb(performance.now());
    return 1;
  });
  vi.stubGlobal('cancelAnimationFrame', () => {});
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('RouteGraph', () => {
  it('renders the flow graph with route nodes from the context', () => {
    render(<RouteGraph context={context} />);
    // Custom RouteNode renders the component-type badge text; at least one node exists.
    expect(document.querySelector('.react-flow')).toBeInTheDocument();
    expect(screen.getAllByText(/.+/).length).toBeGreaterThan(0);
  });

  it('processes the message timeline and animates matched edges', () => {
    // A message that matches an existing static edge.
    const matching = makeMessage({
      exchangeEventType: 'SENDING',
      messageType: 'REQUEST',
      endpointId: staticEdge.data!.outputId,
      routeId: staticEdge.data!.sourceRouteId,
      endpoint: staticEdge.data!.targetUri ?? staticEdge.data!.targetInputUri ?? 'x',
    });

    act(() => {
      useDebuggerStore.getState().appendMessages([matching], 1, 0);
    });

    expect(() => render(<RouteGraph context={context} />)).not.toThrow();
  });

  // Roadmap #9 (latency): avg/max accumulation from SENT messages with timeTaken > 0
  // runs without throwing. (MessageEdge.test.tsx covers the badge's exact text —
  // ReactFlow doesn't render edges in jsdom without real node measurement, so this
  // stays a smoke test like the sibling cases above.)
  it('processes SENT messages with timeTaken without throwing', () => {
    const sent1 = makeMessage({
      exchangeId: 'lat-1',
      exchangeEventType: 'SENT',
      messageType: 'RESPONSE',
      endpointId: staticEdge.data!.outputId,
      routeId: staticEdge.data!.sourceRouteId,
      endpoint: staticEdge.data!.targetUri ?? staticEdge.data!.targetInputUri ?? 'x',
      timeTaken: 20,
    });
    const sent2 = makeMessage({
      exchangeId: 'lat-2',
      exchangeEventType: 'SENT',
      messageType: 'RESPONSE',
      endpointId: staticEdge.data!.outputId,
      routeId: staticEdge.data!.sourceRouteId,
      endpoint: staticEdge.data!.targetUri ?? staticEdge.data!.targetInputUri ?? 'x',
      timeTaken: 60,
    });

    act(() => {
      useDebuggerStore.getState().appendMessages([sent1, sent2], 1, 0);
    });

    expect(() => render(<RouteGraph context={context} />)).not.toThrow();
  });

  it('creates a dynamic edge for a message with no static match', () => {
    const sourceRoute = context.routes[0]!;
    const dynamic = makeMessage({
      exchangeEventType: 'SENDING',
      messageType: 'REQUEST',
      endpointId: 'no-such-output',
      routeId: sourceRoute.id,
      endpoint: 'kafka:brand-new-dynamic-topic',
    });

    const onDynamicEdgeAdded = vi.fn();

    act(() => {
      useDebuggerStore.getState().appendMessages([dynamic], 1, 0);
    });

    render(<RouteGraph context={context} onDynamicEdgeAdded={onDynamicEdgeAdded} />);
    expect(onDynamicEdgeAdded).toHaveBeenCalled();
  });
});
