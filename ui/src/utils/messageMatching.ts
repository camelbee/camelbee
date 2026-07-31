import type { Message } from '@/types';
import type { MessageEdge } from './routeGraph';
import { stripQuery } from './endpointParser';

/* ------------------------------------------------------------------ */
/*  Helpers                                                           */
/* ------------------------------------------------------------------ */

function stripDoubleSlashes(s: string): string {
  return s.replace(/\/\//g, '');
}

/**
 * Compare two endpoint definitions allowing query-param reordering.
 * Port of MessageHelper.compareEndpointDefinitionsEqual.
 */
function compareEndpointDefinitionsEqual(a: string, b: string): boolean {
  if (a === b) return true;

  const partsA = a.split('?');
  const partsB = b.split('?');
  if (partsA.length !== 2 || partsB.length !== 2) return false;
  if (partsA[0]!.toLowerCase() !== partsB[0]!.toLowerCase()) return false;

  const parseParams = (qs: string) => {
    const map = new Map<string, string>();
    for (const pair of qs.split('&')) {
      const [k, v] = pair.split('=');
      if (k) map.set(k, v ?? '');
    }
    return map;
  };

  const mapA = parseParams(partsA[1]!);
  const mapB = parseParams(partsB[1]!);
  if (mapA.size !== mapB.size) return false;

  for (const [k, v] of mapA) {
    if (mapB.get(k) !== v) return false;
  }
  return true;
}

/**
 * True when the message's endpoint matches any of the edge's target URIs once query strings are
 * stripped from both sides and case is ignored.
 */
function targetBaseMatches(
  msgEndpoint: string,
  ...targets: (string | null | undefined)[]
): boolean {
  const msgBase = stripQuery(msgEndpoint).toLowerCase();
  return targets.some(
    (target) => !!target && stripQuery(target).toLowerCase() === msgBase,
  );
}

/* ------------------------------------------------------------------ */
/*  matchMessageToEdge                                                */
/* ------------------------------------------------------------------ */

/** True when the message's endpoint is one of the edge's target URIs. */
function edgeTargetsEndpoint(edge: MessageEdge, msgEndpoint: string): boolean {
  const data = edge.data;
  if (!data) return false;

  const targetUri = data.targetUri ? stripDoubleSlashes(data.targetUri) : null;
  const targetRouteId = data.targetRouteId ? stripDoubleSlashes(data.targetRouteId) : null;
  const targetInputUri = data.targetInputUri ? stripDoubleSlashes(data.targetInputUri) : null;

  return (
    (targetUri !== null && targetUri === msgEndpoint) ||
    (targetRouteId !== null && targetRouteId === msgEndpoint) ||
    (targetInputUri !== null && targetInputUri === msgEndpoint) ||
    (targetUri !== null && compareEndpointDefinitionsEqual(targetUri, msgEndpoint)) ||
    (targetRouteId !== null && compareEndpointDefinitionsEqual(targetRouteId, msgEndpoint)) ||
    (targetInputUri !== null && compareEndpointDefinitionsEqual(targetInputUri, msgEndpoint)) ||
    // Query-stripped comparison (roadmap #3, message side). The producer's URI carries behavioral
    // params the consumer's input never has - to[direct:x?block=true] against From[direct:x] - so
    // the exact and reordering comparisons above both fail.
    targetBaseMatches(msgEndpoint, targetUri, targetRouteId, targetInputUri)
  );
}

/** True when the tracer's routeId names this edge's source, by route id or by input URI. */
function edgeHasSource(edge: MessageEdge, msgRouteId: string): boolean {
  const data = edge.data;
  if (!data) return false;
  const sourceRouteId = stripDoubleSlashes(data.sourceRouteId);
  const sourceInputUri = data.sourceInputUri ? stripDoubleSlashes(data.sourceInputUri) : null;
  return sourceRouteId === msgRouteId || (sourceInputUri !== null && sourceInputUri === msgRouteId);
}

/**
 * Find the edge that a traced message belongs to.
 * Port of CamelComponent.checkLinkInternal.
 *
 * <p>Resolved in passes, strongest evidence first. The node id alone is not sufficient: one EIP node
 * fans out to several endpoints, so a `recipientList` with three targets produces three edges that
 * all carry the same `outputId`. Matching on the node id without also checking the endpoint would
 * attach every one of those messages to an arbitrary sibling. Conversely the endpoint alone is not
 * sufficient either, because a `multicast` and a `recipientList` in the same route can both target
 * it - which is what the node id disambiguates.
 *
 * <p>Edges are scanned in reverse within each pass because error-handler edges are appended last.
 */
export function matchMessageToEdge(
  message: Message,
  edges: MessageEdge[],
): MessageEdge | null {
  if (!message.routeId || !message.endpoint) return null;

  const msgEndpoint = stripDoubleSlashes(message.endpoint);
  const msgRouteId = stripDoubleSlashes(message.routeId);
  const { endpointId } = message;

  // Pass 1 - error handler edges, which are synthetic and take precedence.
  for (let i = edges.length - 1; i >= 0; i--) {
    const edge = edges[i]!;
    const data = edge.data;
    if (!data?.isErrorHandler || !data.targetRouteId) continue;

    const targetRouteId = stripDoubleSlashes(data.targetRouteId);
    const targetInputUri = data.targetInputUri ? stripDoubleSlashes(data.targetInputUri) : null;
    if (
      edgeHasSource(edge, msgRouteId) &&
      (targetRouteId === msgEndpoint || (targetInputUri !== null && targetInputUri === msgEndpoint))
    ) {
      return edge;
    }
  }

  // Pass 2 - the node id AND the endpoint agree. Strongest evidence, and the only pass that can
  // separate the edges of a fan-out node from each other.
  if (endpointId) {
    for (let i = edges.length - 1; i >= 0; i--) {
      const edge = edges[i]!;
      if (edge.data?.outputId === endpointId && edgeTargetsEndpoint(edge, msgEndpoint)) {
        return edge;
      }
    }
  }

  // Pass 3 - the source route AND the endpoint agree, ignoring the node id. Ahead of the passes
  // below because a node id on its own says nothing about which of that node's several targets a
  // hop went to: a dynamicRouter visiting two endpoints stamps both with the same id, and matching
  // on it alone would pile both onto whichever of them has an edge.
  for (let i = edges.length - 1; i >= 0; i--) {
    const edge = edges[i]!;
    if (!edge.data) continue;
    if (edgeHasSource(edge, msgRouteId) && edgeTargetsEndpoint(edge, msgEndpoint)) {
      return edge;
    }
  }

  if (endpointId) {
    // Pass 4 - the node id AND the source route agree, but the endpoint does not. Covers a target
    // the route model could not resolve statically, such as toD("direct:x${exchangeProperty.y}"),
    // where the traced endpoint is the resolved value and the edge holds the expression.
    for (let i = edges.length - 1; i >= 0; i--) {
      const edge = edges[i]!;
      if (edge.data?.outputId === endpointId && edgeHasSource(edge, msgRouteId)) {
        return edge;
      }
    }

    // Pass 5 - the node id alone, last resort. Reached when the tracer attributes the hop to a
    // different route than the one owning the node, which happens for routingSlip and dynamicRouter
    // continuations.
    for (let i = edges.length - 1; i >= 0; i--) {
      const edge = edges[i]!;
      if (edge.data?.outputId === endpointId) {
        return edge;
      }
    }
  }

  return null;
}

/* ------------------------------------------------------------------ */
/*  Interaction builder                                               */
/* ------------------------------------------------------------------ */

export interface Interaction {
  exchangeId: string;
  request: Message | null;
  response: Message | null;
  isError: boolean;
}

/**
 * Group messages matched to an edge into request/response pairs keyed by
 * exchangeId. A single exchangeId may produce multiple pairs (Roadmap #18):
 * Camel redeliveries/retries reuse the same exchangeId for every attempt, so
 * a new REQUEST always starts a new pair rather than overwriting the
 * previous attempt's request/response.
 */
export function buildInteractionsForEdge(
  messages: Message[],
  edge: MessageEdge,
  allEdges: MessageEdge[] = [edge],
): Interaction[] {
  // Resolve against the whole graph and keep only the messages this edge actually wins. Asking
  // "could this message match this edge?" in isolation is not the same question: two edges of a
  // fan-out node share a source and a target, so each would claim the other's messages and both
  // would show every hop twice.
  const matched = messages.filter((m) => matchMessageToEdge(m, allEdges) === edge);

  type Pair = { request: Message | null; response: Message | null };
  const byExchange = new Map<string, Pair[]>();

  for (const m of matched) {
    let pairs = byExchange.get(m.exchangeId);
    if (!pairs) {
      pairs = [];
      byExchange.set(m.exchangeId, pairs);
    }
    if (m.messageType === 'REQUEST') {
      pairs.push({ request: m, response: null });
    } else {
      // RESPONSE or ERROR_RESPONSE: fill the most recent pair still awaiting
      // a response (a retry), or open a new response-only pair if none exists.
      const open = [...pairs].reverse().find((p) => p.response === null);
      if (open) {
        open.response = m;
      } else {
        pairs.push({ request: null, response: m });
      }
    }
  }

  return Array.from(byExchange.entries()).flatMap(([exchangeId, pairs]) =>
    pairs.map((pair) => ({
      exchangeId,
      request: pair.request,
      response: pair.response,
      isError: pair.response?.messageType === 'ERROR_RESPONSE',
    })),
  );
}
