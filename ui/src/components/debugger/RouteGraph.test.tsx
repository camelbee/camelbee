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
  /**
   * A routingSlip or dynamicRouter sends its next target from inside the previous target's
   * continuation, so the tracer's routeId still names that previous callee. Trusting it draws the
   * hop from the wrong node - the arrow appears to come from whichever route ran just before.
   *
   * A dynamicRouter is the case that matters: it is collected as an output but contributes no
   * static edge, because its targets are only known per exchange. So its hops always arrive here,
   * and the node id is the only thing that says which route they belong to.
   */
  describe('dynamic edge source', () => {
    const dynamicRouterContext = {
      name: 'ctx',
      routes: [
        {
          id: 'ownerRoute',
          input: 'From[direct:owner]',
          outputs: [
            {
              id: 'dynamicRouter1',
              description: 'DynamicRouter[header{next}]',
              delimiter: null,
              type: 'org.apache.camel.model.DynamicRouterDefinition',
              outputs: null,
            },
          ],
          rest: false,
          errorHandler: null,
        },
        { id: 'previousCallee', input: 'From[direct:previous]', outputs: [], rest: false, errorHandler: null },
      ],
    } as unknown as CamelBeeContext;

    it('uses the route that owns the node, not the route the tracer reported', () => {
      const hop = makeMessage({
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        endpointId: 'dynamicRouter1',
        // the drift: the tracer names the previously called route
        routeId: 'direct:previous',
        endpoint: 'kafka:computed-at-runtime',
      });

      const onDynamicEdgeAdded = vi.fn();
      act(() => {
        useDebuggerStore.getState().appendMessages([hop], 1, 0);
      });
      render(<RouteGraph context={dynamicRouterContext} onDynamicEdgeAdded={onDynamicEdgeAdded} />);

      expect(onDynamicEdgeAdded).toHaveBeenCalled();
      const created = onDynamicEdgeAdded.mock.calls[0]![0];
      expect(created.data.sourceRouteId).toBe('ownerRoute');
      expect(created.source).toBe('route-ownerRoute');
    });

    it('falls back to the reported routeId when the node id is not in the topology', () => {
      const hop = makeMessage({
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        endpointId: 'not-a-node-here',
        routeId: 'direct:previous',
        endpoint: 'kafka:computed-at-runtime',
      });

      const onDynamicEdgeAdded = vi.fn();
      act(() => {
        useDebuggerStore.getState().appendMessages([hop], 1, 0);
      });
      render(<RouteGraph context={dynamicRouterContext} onDynamicEdgeAdded={onDynamicEdgeAdded} />);

      expect(onDynamicEdgeAdded).toHaveBeenCalled();
      expect(onDynamicEdgeAdded.mock.calls[0]![0].data.sourceRouteId).toBe('previousCallee');
    });
  });
});
