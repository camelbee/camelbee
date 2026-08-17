import dagre from '@dagrejs/dagre';
import type { Node, Edge } from '@xyflow/react';
import type { CamelBeeContext, CamelRoute, CamelRouteOutput } from '@/types';
import {
  extractInputUri,
  extractComponentType,
  extractStaticEndpointsFromOutput,
  outputReferencesInput,
  stripQuery,
} from './endpointParser';

/* ------------------------------------------------------------------ */
/*  Types                                                             */
/* ------------------------------------------------------------------ */

export interface RouteNodeData {
  label: string;
  componentType: string;
  kind: 'consumer' | 'internal' | 'producer' | 'error';
  routeId?: string;
  isCallable?: boolean;
  /** Route's <description> text, for the hover tooltip (roadmap #1+15). */
  description?: string;
  /** Full input URI, for the hover tooltip. */
  inputUri?: string;
  /** Full, untruncated URI for a producer (external endpoint) node - the label is truncated. */
  fullUri?: string;
  /** Error-handler target input URI, for the hover tooltip. */
  errorHandler?: string;
  isRest?: boolean;
  [key: string]: unknown;
}

export interface ActiveFlow {
  id: number;
  type: 'REQUEST' | 'RESPONSE' | 'ERROR_RESPONSE';
  label: string;
}

export interface MessageEdgeData {
  outputId: string;
  sourceRouteId: string;
  sourceInputUri?: string;
  targetRouteId?: string;
  targetInputUri?: string;
  targetUri?: string;
  messageCount: number;
  hasError: boolean;
  animated: boolean;
  isErrorHandler: boolean;
  activeFlows: ActiveFlow[];
  /** Average/max elapsed ms across SENT messages on this edge (roadmap #9). */
  avgTimeTaken?: number;
  maxTimeTaken?: number;
  /**
   * Number of individual REQUEST attempts on this edge, set only when it exceeds messageCount -
   * i.e. a redelivery retried the same exchange (roadmap #18). messageCount stays a distinct-
   * exchange/throughput count; this is the separate "there were retries here" signal.
   */
  retryCount?: number;
  [key: string]: unknown;
}

export type RouteNode = Node<RouteNodeData>;
export type MessageEdge = Edge<MessageEdgeData>;

/* ------------------------------------------------------------------ */
/*  Constants                                                         */
/* ------------------------------------------------------------------ */

const NODE_WIDTH = 220;
const NODE_HEIGHT = 80;

/* ------------------------------------------------------------------ */
/*  Public helpers                                                    */
/* ------------------------------------------------------------------ */

export { sanitize, makeNodeId, makeProducerId, makeEdgeId, truncateLabel };

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function sanitize(s: string): string {
  return s.replace(/[^a-zA-Z0-9_-]/g, '_');
}

/**
 * Build the set of direct:/seda: input URIs that are called by at least one
 * other route's outputs. Any direct:/seda: route NOT in this set is an
 * entry-point consumer (e.g. REST routes that Camel exposes as direct:).
 */
function findCalledInputUris(routes: CamelRoute[]): Set<string> {
  const called = new Set<string>();
  for (const route of routes) {
    // Check outputs for To[direct:...], DynamicTo[direct:...], etc.
    const allOutputs = flattenOutputsStatic(route.outputs);
    for (const output of allOutputs) {
      for (const candidate of routes) {
        const candidateInput = candidate.input.toLowerCase();
        if (
          !candidateInput.startsWith('from[direct:') &&
          !candidateInput.startsWith('from[seda:')
        ) {
          continue;
        }
        const inputTrimmed = extractInputUri(candidate.input);
        if (outputReferencesInput(output, inputTrimmed)) {
          called.add(inputTrimmed.toLowerCase());
        }
      }
    }
    // Also mark error handler targets as called
    if (route.errorHandler) {
      called.add(route.errorHandler.toLowerCase());
    }
  }
  return called;
}

/** Flatten nested output tree (static version for pre-scan). */
function flattenOutputsStatic(outputs: CamelRouteOutput[]): CamelRouteOutput[] {
  const result: CamelRouteOutput[] = [];
  for (const o of outputs) {
    result.push(o);
    if (o.outputs && o.outputs.length > 0) {
      result.push(...flattenOutputsStatic(o.outputs));
    }
  }
  return result;
}

function isConsumerRoute(route: CamelRoute, calledUris: Set<string>): boolean {
  if (route.rest) return true;
  const inputUri = extractInputUri(route.input).toLowerCase();
  // Non-direct/seda routes are always consumers (kafka, http, timer, etc.)
  if (!inputUri.startsWith('direct:') && !inputUri.startsWith('seda:')) return true;
  // direct:/seda: routes that no other route calls are entry-point consumers
  return !calledUris.has(inputUri);
}

function makeNodeId(routeId: string): string {
  return `route-${sanitize(routeId)}`;
}

function makeProducerId(uri: string): string {
  return `producer-${sanitize(uri)}`;
}

function makeEdgeId(source: string, target: string, outputId: string): string {
  return `edge-${source}-${target}-${sanitize(outputId)}`;
}

function truncateLabel(label: string, max = 32): string {
  return label.length > max ? label.substring(0, max) + '…' : label;
}

/**
 * Build a human-readable label for a route node.
 *
 * Roadmap #1+15 originally preferred the route's `<description>` here, with the id only on
 * hover. Reverted (2026-08-15, user decision): a truncated description reads as an awkward
 * prose fragment in a dense graph, and is harder to scan / cross-reference against the route id
 * used in the Java source than the id itself. The id is now always the label; the description
 * (when present) still shows in full on hover (see `buildTooltip` in RouteNode.tsx).
 */
function routeLabel(route: CamelRoute): string {
  const inputUri = extractInputUri(route.input);
  if (route.rest) return `REST ${route.id}`;
  // If route ID is auto-generated (e.g. "route1", "route2"), use the input URI instead - it's
  // more identifying than a meaningless generated id.
  if (/^route\d+$/i.test(route.id)) return truncateLabel(inputUri);
  return truncateLabel(route.id);
}

/* ------------------------------------------------------------------ */
/*  Graph builder                                                     */
/* ------------------------------------------------------------------ */

export function buildRouteGraph(context: CamelBeeContext): {
  nodes: RouteNode[];
  edges: MessageEdge[];
} {
  const nodes: RouteNode[] = [];
  const edges: MessageEdge[] = [];
  const drawnRoutes = new Set<string>();
  const drawnProducers = new Set<string>();
  const routeById = new Map<string, CamelRoute>();

  for (const r of context.routes) {
    routeById.set(r.id, r);
  }

  /** Every route input URI, query-stripped and lowercased, for "is this endpoint a route?" checks. */
  const routeInputUris = new Set(
    context.routes.map((r) => stripQuery(extractInputUri(r.input)).toLowerCase()),
  );

  /** True when some route consumes this URI, i.e. step 1 has already drawn an edge to it. */
  function isConsumedByRoute(uri: string): boolean {
    return routeInputUris.has(stripQuery(uri).toLowerCase());
  }

  /* -- Node / Edge creation helpers -- */

  function addRouteNode(
    route: CamelRoute,
    kind: RouteNodeData['kind'],
  ): string {
    const nodeId = makeNodeId(route.id);
    if (drawnRoutes.has(route.id)) return nodeId;
    drawnRoutes.add(route.id);

    const inputUri = extractInputUri(route.input);
    const componentType =
      kind === 'error' ? 'error' : extractComponentType(inputUri);

    nodes.push({
      id: nodeId,
      type: 'routeNode',
      position: { x: 0, y: 0 }, // dagre will set this
      data: {
        label: routeLabel(route),
        componentType,
        kind,
        routeId: route.id,
        isCallable: kind === 'consumer',
        description: route.routeDescription ?? undefined,
        inputUri,
        errorHandler: route.errorHandler ?? undefined,
        isRest: route.rest,
      },
    });
    return nodeId;
  }

  function addProducerNode(uri: string): string {
    const nodeId = makeProducerId(uri);
    if (drawnProducers.has(uri)) return nodeId;
    drawnProducers.add(uri);

    nodes.push({
      id: nodeId,
      type: 'routeNode',
      position: { x: 0, y: 0 },
      data: {
        label: truncateLabel(uri),
        componentType: extractComponentType(uri),
        kind: 'producer',
        fullUri: uri,
      },
    });
    return nodeId;
  }

  function addEdge(
    sourceId: string,
    targetId: string,
    output: CamelRouteOutput,
    sourceRouteId: string,
    targetRouteId?: string,
    targetUri?: string,
    isErrorHandler = false,
  ): void {
    const edgeId = makeEdgeId(sourceId, targetId, output.id);
    // Prevent duplicate edges
    if (edges.some((e) => e.id === edgeId)) return;

    // Resolve input URIs for message matching (tracer reports input URI as routeId)
    const sourceRoute = routeById.get(sourceRouteId);
    const sourceInputUri = sourceRoute ? extractInputUri(sourceRoute.input) : undefined;
    const targetRoute = targetRouteId ? routeById.get(targetRouteId) : undefined;
    const targetInputUri = targetRoute ? extractInputUri(targetRoute.input) : undefined;

    edges.push({
      id: edgeId,
      source: sourceId,
      target: targetId,
      type: 'messageEdge',
      data: {
        outputId: output.id,
        sourceRouteId,
        sourceInputUri,
        targetRouteId,
        targetInputUri,
        targetUri,
        messageCount: 0,
        hasError: false,
        animated: false,
        isErrorHandler,
        activeFlows: [],
      },
    });
  }

  /* -- Recursive sibling walker (mirrors RouteService.doDrawSiblings) -- */

  function walkOutputs(route: CamelRoute, sourceNodeId: string): void {
    const allOutputs = flattenOutputs(route.outputs);

    for (const output of allOutputs) {
      // Step 1: Match internal routes (direct:/seda:)
      for (const candidate of context.routes) {
        const candidateInput = candidate.input.toLowerCase();
        if (
          !candidateInput.startsWith('from[direct:') &&
          !candidateInput.startsWith('from[seda:')
        ) {
          continue;
        }
        const inputTrimmed = extractInputUri(candidate.input);
        if (outputReferencesInput(output, inputTrimmed)) {
          const alreadyDrawn = drawnRoutes.has(candidate.id);
          const childNodeId = addRouteNode(candidate, 'internal');
          addEdge(
            sourceNodeId,
            childNodeId,
            output,
            route.id,
            candidate.id,
          );
          // Only recurse into children if not already walked
          if (!alreadyDrawn) {
            walkOutputs(candidate, childNodeId);
          }
        }
      }

      // Step 2: Everything the output targets that is NOT a route drawn by step 1 becomes a
      // producer node. That covers external endpoints (kafka:, http:, mock:, log:, file:) and
      // also internal direct:/seda: endpoints that no route consumes - a to("seda:x") drained
      // only by poll()/pollEnrich() has no From[seda:x] to link to, and would otherwise vanish
      // from the graph along with every message traced on it.
      const targets = extractStaticEndpointsFromOutput(output, true);
      if (targets) {
        for (const uri of targets) {
          if (isConsumedByRoute(uri)) continue;
          const producerNodeId = addProducerNode(uri);
          addEdge(sourceNodeId, producerNodeId, output, route.id, undefined, uri);
        }
      }
    }
  }

  /** Flatten nested output tree into a flat list. */
  function flattenOutputs(outputs: CamelRouteOutput[]): CamelRouteOutput[] {
    const result: CamelRouteOutput[] = [];
    for (const o of outputs) {
      result.push(o);
      if (o.outputs && o.outputs.length > 0) {
        result.push(...flattenOutputs(o.outputs));
      }
    }
    return result;
  }

  /* -- Pass 1: Consumer routes + children -- */

  const calledUris = findCalledInputUris(context.routes);
  const consumers = context.routes.filter((r) => isConsumerRoute(r, calledUris));
  for (const route of consumers) {
    const nodeId = addRouteNode(route, 'consumer');
    walkOutputs(route, nodeId);
  }

  /* -- Pass 2: Orphan routes (not yet drawn) -- */

  for (const route of context.routes) {
    if (!drawnRoutes.has(route.id)) {
      const nodeId = addRouteNode(route, 'internal');
      walkOutputs(route, nodeId);
    }
  }

  /* -- Pass 3: Error handler edges -- */

  for (const route of context.routes) {
    if (!route.errorHandler) continue;

    // Find the error handler route
    const errorRoute = context.routes.find((r) => {
      const inputUri = extractInputUri(r.input);
      return inputUri === route.errorHandler;
    });
    if (!errorRoute) continue;

    const sourceId = makeNodeId(route.id);
    const targetId = addRouteNode(errorRoute, 'error');

    // Create a synthetic output for the error handler edge
    const syntheticOutput: CamelRouteOutput = {
      id: `errorHandler-${route.id}`,
      description: `ErrorHandler[${route.errorHandler}]`,
      delimiter: null,
      type: 'errorHandler',
      outputs: [],
    };
    addEdge(sourceId, targetId, syntheticOutput, route.id, errorRoute.id, undefined, true);

    // Also walk the error handler route's outputs
    if (!drawnRoutes.has(errorRoute.id)) {
      // Already added via addRouteNode above
    }
    walkOutputs(errorRoute, targetId);
  }

  /* -- Dagre layout -- */

  const g = new dagre.graphlib.Graph();
  g.setGraph({ rankdir: 'LR', ranksep: 180, nodesep: 80 });
  g.setDefaultEdgeLabel(() => ({}));

  for (const node of nodes) {
    g.setNode(node.id, { width: NODE_WIDTH, height: NODE_HEIGHT });
  }
  for (const edge of edges) {
    g.setEdge(edge.source, edge.target);
  }

  dagre.layout(g);

  for (const node of nodes) {
    const pos = g.node(node.id);
    if (pos) {
      node.position = {
        x: pos.x - NODE_WIDTH / 2,
        y: pos.y - NODE_HEIGHT / 2,
      };
    }
  }

  /* -- Align all consumer nodes to the leftmost x position -- */
  const consumerNodes = nodes.filter((n) => n.data.kind === 'consumer');
  if (consumerNodes.length > 0) {
    const minX = Math.min(...consumerNodes.map((n) => n.position.x));
    for (const node of consumerNodes) {
      node.position.x = minX;
    }

    // After aligning X, fix Y overlaps caused by collapsing different ranks
    consumerNodes.sort((a, b) => a.position.y - b.position.y);
    const minGap = NODE_HEIGHT + 20;
    for (let i = 1; i < consumerNodes.length; i++) {
      const prev = consumerNodes[i - 1]!;
      const curr = consumerNodes[i]!;
      if (curr.position.y - prev.position.y < minGap) {
        curr.position.y = prev.position.y + minGap;
      }
    }
  }

  return { nodes, edges };
}

/**
 * Maps each route id to the component type of the endpoint it consumes from - `timer`, `rest`,
 * `jms`, `file` and so on.
 *
 * Built from the same graph nodes the topology renders, so the label a route carries in the
 * waterfall is by construction the one shown on its node. Deriving it separately from the route's
 * input URI would be a second implementation of `extractComponentType`'s edge cases (rest verbs,
 * dynamic endpoints) and the two would drift.
 */
export function buildRouteTypeIndex(nodes: RouteNode[]): Map<string, string> {
  const index = new Map<string, string>();
  for (const node of nodes) {
    const { routeId, componentType } = node.data;
    if (routeId && componentType) index.set(routeId, componentType);
  }
  return index;
}
