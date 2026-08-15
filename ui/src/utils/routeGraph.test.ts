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

// Roadmap #1+15 (route descriptions): id as node label (see routeLabel's doc comment for why
// description was reverted from the label, 2026-08-15), description carried as tooltip metadata.
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

  it('uses the route id as the label even when routeDescription is present, but still carries the description for the tooltip', () => {
    const { nodes } = buildRouteGraph(
      contextWith({ routeDescription: 'Handles widget requests' }),
    );
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.label).toBe('widgetRoute');
    expect(node.data.description).toBe('Handles widget requests');
  });

  it('has no description metadata when routeDescription is absent', () => {
    const { nodes } = buildRouteGraph(contextWith({}));
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.label).toBe('widgetRoute');
    expect(node.data.description).toBeUndefined();
  });

  it('prefixes REST on the id label regardless of routeDescription', () => {
    const { nodes } = buildRouteGraph(
      contextWith({ rest: true, routeDescription: 'Order API' }),
    );
    const node = nodes.find((n) => n.data.routeId === 'widgetRoute')!;
    expect(node.data.label).toBe('REST widgetRoute');
    expect(node.data.description).toBe('Order API');
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

/**
 * An internal endpoint that no route consumes still has to appear. `to("seda:x")` drained only by
 * `poll()`/`pollEnrich()` has no `From[seda:x]` to link to, so before this it produced neither a
 * node nor an edge — and every message traced on that hop was silently unattributable.
 */
describe('buildRouteGraph — unconsumed internal endpoints', () => {
  function contextWithOutput(description: string, extraRoutes: CamelBeeContext['routes'] = []) {
    return {
      ...context,
      routes: [
        {
          id: 'producerRoute',
          input: 'From[direct:producer]',
          outputs: [
            { id: 'sedaOut', description, delimiter: null, type: 'org.apache.camel.model.ToDefinition', outputs: null },
          ],
          rest: false,
          errorHandler: null,
        },
        ...extraRoutes,
      ],
    } as unknown as CamelBeeContext;
  }

  it('renders a producer node for a seda: endpoint that no route consumes', () => {
    const { nodes, edges } = buildRouteGraph(contextWithOutput('to[seda:southbound]'));

    const producer = nodes.find((n) => n.id === 'producer-seda_southbound');
    expect(producer).toBeDefined();
    expect(producer!.data.kind).toBe('producer');
    expect(producer!.data.componentType).toBe('seda');
    // The label is truncated for display; fullUri is what the tooltip shows on hover, so a long
    // endpoint URI (e.g. http://host:port/long/path) isn't just re-shown truncated (bug found
    // 2026-08-15 - the tooltip fell back to the already-truncated label with no full-URI field).
    expect(producer!.data.fullUri).toBe('seda:southbound');

    expect(edges.some((e) => e.source === 'route-producerRoute' && e.target === 'producer-seda_southbound'))
      .toBe(true);
  });

  it('still links to the route instead of a producer node when one does consume it', () => {
    const consumer = {
      id: 'consumerRoute',
      input: 'From[seda:southbound]',
      outputs: [],
      rest: false,
      errorHandler: null,
    } as unknown as CamelBeeContext['routes'][number];

    const { nodes, edges } = buildRouteGraph(contextWithOutput('to[seda:southbound]', [consumer]));

    expect(nodes.find((n) => n.id === 'producer-seda_southbound')).toBeUndefined();
    expect(edges.some((e) => e.source === 'route-producerRoute' && e.target === 'route-consumerRoute'))
      .toBe(true);
  });

  it('does not duplicate a direct: target that a route already consumes', () => {
    const consumer = {
      id: 'targetRoute',
      input: 'From[direct:target]',
      outputs: [],
      rest: false,
      errorHandler: null,
    } as unknown as CamelBeeContext['routes'][number];

    const { nodes } = buildRouteGraph(contextWithOutput('to[direct:target?block=true]', [consumer]));

    // query string on the producer side must not defeat the "is this a route?" check
    expect(nodes.find((n) => n.id === 'producer-direct_target')).toBeUndefined();
  });
});

/**
 * A dynamicRouter is reported by the backend so that traced messages carrying its node id can be
 * resolved back to the route that owns it. It must not draw anything: its targets are chosen per
 * exchange, so any node or edge derived from it statically would be an invention.
 */
describe('buildRouteGraph — dynamicRouter outputs', () => {
  const context = {
    ...routesFixture,
    routes: [
      {
        id: 'ownerRoute',
        input: 'From[direct:owner]',
        outputs: [
          {
            id: 'to1',
            description: 'to[mock:real]',
            delimiter: null,
            type: 'org.apache.camel.model.ToDefinition',
            outputs: null,
          },
          {
            id: 'dynamicRouter1',
            description: 'DynamicRouter[bean[method:computeEndpoint]]',
            delimiter: ',',
            type: 'org.apache.camel.model.DynamicRouterDefinition',
            outputs: null,
          },
        ],
        rest: false,
        errorHandler: null,
      },
    ],
  } as unknown as CamelBeeContext;

  it('draws no node and no edge for the router itself', () => {
    const { nodes, edges } = buildRouteGraph(context);

    // the route and its one real producer, nothing else
    expect(nodes.map((n) => n.id).sort()).toEqual(['producer-mock_real', 'route-ownerRoute']);
    expect(edges).toHaveLength(1);
    expect(edges[0]!.data!.outputId).toBe('to1');

    expect(edges.some((e) => e.data!.outputId === 'dynamicRouter1')).toBe(false);
    expect(nodes.some((n) => n.id.toLowerCase().includes('dynamicrouter'))).toBe(false);
  });
});
