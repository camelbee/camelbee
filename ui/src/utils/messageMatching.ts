import type { Message } from '@/types';
import type { MessageEdge } from './routeGraph';

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
        compareEndpointDefinitionsEqual(targetInputUri, msgEndpoint));

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
 * exchangeId.
 */
export function buildInteractionsForEdge(
  messages: Message[],
  edge: MessageEdge,
): Interaction[] {
  const matched = messages.filter((m) => matchMessageToEdge(m, [edge]) !== null);

  const byExchange = new Map<string, { request: Message | null; response: Message | null }>();

  for (const m of matched) {
    let entry = byExchange.get(m.exchangeId);
    if (!entry) {
      entry = { request: null, response: null };
      byExchange.set(m.exchangeId, entry);
    }
    if (m.messageType === 'REQUEST') {
      entry.request = m;
    } else {
      // RESPONSE or ERROR_RESPONSE
      entry.response = m;
    }
  }

  return Array.from(byExchange.entries()).map(([exchangeId, pair]) => ({
    exchangeId,
    request: pair.request,
    response: pair.response,
    isError: pair.response?.messageType === 'ERROR_RESPONSE',
  }));
}
