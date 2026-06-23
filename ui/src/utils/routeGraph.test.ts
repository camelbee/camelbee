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
