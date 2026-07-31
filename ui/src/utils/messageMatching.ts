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

/**
 * Find the edge that a traced message belongs to.
 * Port of CamelComponent.checkLinkInternal.
 */
export function matchMessageToEdge(
  message: Message,
  edges: MessageEdge[],
): MessageEdge | null {
  if (!message.routeId || !message.endpoint) return null;

  const msgEndpoint = stripDoubleSlashes(message.endpoint);
  const msgRouteId = stripDoubleSlashes(message.routeId);

  // Iterate in reverse (error handler edges added last)
  for (let i = edges.length - 1; i >= 0; i--) {
    const edge = edges[i]!;
    const data = edge.data;
    if (!data) continue;

    // Error handler match
    if (data.isErrorHandler && data.targetRouteId) {
      const targetRouteId = stripDoubleSlashes(data.targetRouteId);
      const targetInputUri = data.targetInputUri ? stripDoubleSlashes(data.targetInputUri) : null;
      const sourceMatches =
        data.sourceRouteId === msgRouteId ||
        (data.sourceInputUri && stripDoubleSlashes(data.sourceInputUri) === msgRouteId);
      if (
        sourceMatches &&
        (targetRouteId === msgEndpoint ||
          (targetInputUri !== null && targetInputUri === msgEndpoint))
      ) {
        return edge;
      }
    }

    // Primary: endpointId match
    if (data.outputId && data.outputId === message.endpointId) {
      return edge;
    }

    // Fallback: routeId + endpoint match
    // The tracer may report routeId as either the route's id or its input URI
    const sourceRouteId = stripDoubleSlashes(data.sourceRouteId);
    const sourceInputUri = data.sourceInputUri
      ? stripDoubleSlashes(data.sourceInputUri)
      : null;
    const routeMatches =
      sourceRouteId === msgRouteId ||
      (sourceInputUri !== null && sourceInputUri === msgRouteId);

    if (!routeMatches) continue;

    const targetUri = data.targetUri
      ? stripDoubleSlashes(data.targetUri)
      : null;
    const targetRouteId = data.targetRouteId
      ? stripDoubleSlashes(data.targetRouteId)
      : null;
    const targetInputUri = data.targetInputUri
      ? stripDoubleSlashes(data.targetInputUri)
      : null;

    const endpointMatches =
      (targetUri !== null && targetUri === msgEndpoint) ||
      (targetRouteId !== null && targetRouteId === msgEndpoint) ||
      (targetInputUri !== null && targetInputUri === msgEndpoint) ||
      (targetUri !== null &&
        compareEndpointDefinitionsEqual(targetUri, msgEndpoint)) ||
      (targetRouteId !== null &&
        compareEndpointDefinitionsEqual(targetRouteId, msgEndpoint)) ||
      (targetInputUri !== null &&
        compareEndpointDefinitionsEqual(targetInputUri, msgEndpoint)) ||
      // Query-stripped comparison (roadmap #3, message side). The producer's URI carries behavioral
      // params the consumer's input never has - to[direct:x?block=true] against From[direct:x] - so
      // the exact and reordering comparisons above both fail. This fallback only matters when
      // endpointId is absent, which is exactly what happens on every redelivered attempt: Camel
      // reports the node id on the first send only, so without this every retry after the first
      // disappears from the message panel.
      targetBaseMatches(msgEndpoint, targetUri, targetRouteId, targetInputUri);

    if (endpointMatches) {
      return edge;
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
): Interaction[] {
  const matched = messages.filter((m) => matchMessageToEdge(m, [edge]) !== null);

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
