import { describe, it, expect } from 'vitest';
import type { CamelBeeContext } from '@/types';
import { buildRouteGraph } from './routeGraph';
import routesFixture from '../../mock/routes.json';

const context = routesFixture as unknown as CamelBeeContext;

describe('buildRouteGraph', () => {
  it('builds nodes and edges from the fixture topology', () => {
    const { nodes, edges } = buildRouteGraph(context);

    // One node per route at minimum, plus producer nodes for external endpoints.
    expect(nodes.length).toBeGreaterThanOrEqual(context.routes.length);
    expect(edges.length).toBeGreaterThan(0);

    // Node ids are unique.
    const ids = nodes.map((n) => n.id);
    expect(new Set(ids).size).toBe(ids.length);

    // Every edge references existing nodes.
    const idSet = new Set(ids);
    for (const edge of edges) {
      expect(idSet.has(edge.source)).toBe(true);
      expect(idSet.has(edge.target)).toBe(true);
    }
  });

  it('lets dagre assign a real layout (positions are not all at the origin)', () => {
    const { nodes } = buildRouteGraph(context);

    // If dagre's API broke, positions would stay at the {x:0,y:0} seed.
    const positioned = nodes.filter((n) => n.position.x !== 0 || n.position.y !== 0);
    expect(positioned.length).toBeGreaterThan(0);

    // Layout produces finite coordinates, never NaN/Infinity.
    for (const n of nodes) {
      expect(Number.isFinite(n.position.x)).toBe(true);
      expect(Number.isFinite(n.position.y)).toBe(true);
    }
  });

  it('classifies consumer/producer/error node kinds', () => {
    const { nodes } = buildRouteGraph(context);
    const kinds = new Set(nodes.map((n) => n.data.kind));
    expect(kinds.has('consumer')).toBe(true);
  });
});

// Roadmap #1+15 (route descriptions): description as node label, id fallback,
// plus metadata for the RouteNode hover tooltip.
describe('buildRouteGraph — route descriptions', () => {
  function contextWith(route: Partial<CamelBeeContext['routes'][number]>): CamelBeeContext {
    return {
      ...context,
      routes: [
        {
          id: 'widgetRoute',
          input: 'From[direct:widget]',
          outputs: [],
          rest: false,
          errorHandler: null,
          ...route,
        },
      ],
    };
  }

  it('uses routeDescription as the label when present', () => {
    const { nodes } = buildRouteGraph(
      contextWith({ routeDescription: 'Handles widget requests' }),
    );
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.label).toBe('Handles widget requests');
    expect(node.data.description).toBe('Handles widget requests');
  });

  it('falls back to the route id when routeDescription is absent', () => {
    const { nodes } = buildRouteGraph(contextWith({}));
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.label).toBe('widgetRoute');
    expect(node.data.description).toBeUndefined();
  });

  it('prefixes REST when both rest and routeDescription are set', () => {
    const { nodes } = buildRouteGraph(
      contextWith({ rest: true, routeDescription: 'Order API' }),
    );
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.label).toBe('REST Order API');
    expect(node.data.isRest).toBe(true);
  });

  it('carries inputUri and errorHandler through to node data for the tooltip', () => {
    const { nodes } = buildRouteGraph(
      contextWith({ errorHandler: 'direct:dlq' }),
    );
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.inputUri).toBe('direct:widget');
    expect(node.data.errorHandler).toBe('direct:dlq');
  });
});
