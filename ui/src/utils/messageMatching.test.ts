import { describe, it, expect } from 'vitest';
import type { MessageEdge, MessageEdgeData } from './routeGraph';
import { matchMessageToEdge, buildInteractionsForEdge } from './messageMatching';
import { makeMessage } from '@/test/factories';

function edge(data: Partial<MessageEdgeData>, id = 'e1'): MessageEdge {
  return {
    id,
    source: 's',
    target: 't',
    type: 'messageEdge',
    data: {
      outputId: 'out-1',
      sourceRouteId: 'route1',
      messageCount: 0,
      hasError: false,
      animated: false,
      isErrorHandler: false,
      activeFlows: [],
      ...data,
    },
  };
}

describe('matchMessageToEdge', () => {
  it('returns null when the message lacks routeId or endpoint', () => {
    expect(matchMessageToEdge(makeMessage({ routeId: '' }), [edge({})])).toBeNull();
    expect(matchMessageToEdge(makeMessage({ endpoint: '' }), [edge({})])).toBeNull();
  });

  it('matches primarily on endpointId === outputId', () => {
    const m = makeMessage({ endpointId: 'out-99', routeId: 'whatever', endpoint: 'whatever' });
    const e = edge({ outputId: 'out-99' });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  it('falls back to routeId + endpoint match', () => {
    const m = makeMessage({ endpointId: 'nope', routeId: 'route1', endpoint: 'kafka:orders' });
    const e = edge({ outputId: 'different', sourceRouteId: 'route1', targetUri: 'kafka:orders' });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  it('matches the source by its input URI when routeId is reported as the URI', () => {
    const m = makeMessage({ endpointId: 'nope', routeId: 'direct:in', endpoint: 'kafka:orders' });
    const e = edge({
      outputId: 'different',
      sourceRouteId: 'route1',
      sourceInputUri: 'direct:in',
      targetUri: 'kafka:orders',
    });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  it('treats endpoints as equal when only query-param order differs', () => {
    const m = makeMessage({ endpointId: 'nope', routeId: 'route1', endpoint: 'http:host?a=1&b=2' });
    const e = edge({ outputId: 'x', sourceRouteId: 'route1', targetUri: 'http:host?b=2&a=1' });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  it('matches an error-handler edge on source + target', () => {
    const m = makeMessage({ endpointId: 'nope', routeId: 'route1', endpoint: 'direct:errorHandler' });
    const e = edge({
      isErrorHandler: true,
      sourceRouteId: 'route1',
      targetRouteId: 'direct:errorHandler',
    });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  /**
   * Roadmap #3, message side. The producer sends to `direct:x?block=true` while the consumer's
   * input is `direct:x`, so neither exact equality nor the query-reordering comparison matches.
   * This only bites when endpointId is absent — which is every redelivered attempt, since Camel
   * reports the node id on the first send only.
   */
  it('matches across a query string on the producer side when endpointId is absent', () => {
    const m = makeMessage({
      endpointId: null,
      routeId: 'direct://invokeFlaky',
      endpoint: 'direct://flakyTarget?block=true',
    });
    const e = edge({
      outputId: 'flakyEndpoint',
      sourceRouteId: 'invokeFlakyRoute',
      sourceInputUri: 'direct:invokeFlaky',
      targetRouteId: 'flakyTargetRoute',
      targetInputUri: 'direct:flakyTarget',
    });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  it('ignores case when comparing query-stripped endpoints', () => {
    const m = makeMessage({
      endpointId: null,
      routeId: 'route1',
      endpoint: 'DIRECT://Target?block=true',
    });
    const e = edge({ outputId: 'other', sourceRouteId: 'route1', targetInputUri: 'direct:target' });
    expect(matchMessageToEdge(m, [e])).toBe(e);
  });

  it('does not match a different endpoint just because query strings are stripped', () => {
    const m = makeMessage({ endpointId: null, routeId: 'route1', endpoint: 'direct:other?block=true' });
    const e = edge({ outputId: 'x', sourceRouteId: 'route1', targetInputUri: 'direct:target' });
    expect(matchMessageToEdge(m, [e])).toBeNull();
  });

  it('returns null when nothing matches', () => {
    const m = makeMessage({ endpointId: 'nope', routeId: 'routeX', endpoint: 'kafka:none' });
    expect(matchMessageToEdge(m, [edge({ sourceRouteId: 'route1', targetUri: 'kafka:orders' })])).toBeNull();
  });
});

describe('buildInteractionsForEdge', () => {
  it('pairs request/response by exchangeId for messages on the edge', () => {
    const e = edge({ outputId: 'out-1' });
    const messages = [
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'RESPONSE' }),
      makeMessage({ exchangeId: 'B', endpointId: 'out-1', messageType: 'REQUEST' }),
    ];

    const interactions = buildInteractionsForEdge(messages, e);
    expect(interactions).toHaveLength(2);

    const a = interactions.find((i) => i.exchangeId === 'A')!;
    expect(a.request).not.toBeNull();
    expect(a.response).not.toBeNull();
    expect(a.isError).toBe(false);

    const b = interactions.find((i) => i.exchangeId === 'B')!;
    expect(b.response).toBeNull();
  });

  it('flags interactions whose response is an ERROR_RESPONSE', () => {
    const e = edge({ outputId: 'out-1' });
    const interactions = buildInteractionsForEdge(
      [
        makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST' }),
        makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'ERROR_RESPONSE' }),
      ],
      e,
    );
    expect(interactions[0]!.isError).toBe(true);
  });

  it('ignores messages that do not belong to the edge', () => {
    const e = edge({ outputId: 'out-1' });
    const interactions = buildInteractionsForEdge(
      [makeMessage({ exchangeId: 'Z', endpointId: 'other', routeId: 'x', endpoint: 'y' })],
      e,
    );
    expect(interactions).toHaveLength(0);
  });

  // Roadmap #18 (loop/retry-safe interactions): Camel redeliveries reuse the
  // same exchangeId for every attempt. A new REQUEST must start a new pair
  // instead of overwriting the previous attempt's request/response.
  it('keeps every retry attempt as its own interaction for the same exchangeId', () => {
    const e = edge({ outputId: 'out-1' });
    const messages = [
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST', messageBody: 'req-1' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'ERROR_RESPONSE', messageBody: 'timeout-1' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST', messageBody: 'req-2' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'ERROR_RESPONSE', messageBody: 'timeout-2' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST', messageBody: 'req-3' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'RESPONSE', messageBody: 'success-3' }),
    ];

    const interactions = buildInteractionsForEdge(messages, e);

    expect(interactions).toHaveLength(3);
    expect(interactions.every((i) => i.exchangeId === 'A')).toBe(true);

    expect(interactions[0]!.request?.messageBody).toBe('req-1');
    expect(interactions[0]!.response?.messageBody).toBe('timeout-1');
    expect(interactions[0]!.isError).toBe(true);

    expect(interactions[1]!.request?.messageBody).toBe('req-2');
    expect(interactions[1]!.response?.messageBody).toBe('timeout-2');
    expect(interactions[1]!.isError).toBe(true);

    expect(interactions[2]!.request?.messageBody).toBe('req-3');
    expect(interactions[2]!.response?.messageBody).toBe('success-3');
    expect(interactions[2]!.isError).toBe(false);
  });

  it('interleaves retries on multiple edges without cross-contamination', () => {
    // Two different exchanges, each retried once, sharing the edge.
    const e = edge({ outputId: 'out-1' });
    const messages = [
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST', messageBody: 'A-req-1' }),
      makeMessage({ exchangeId: 'B', endpointId: 'out-1', messageType: 'REQUEST', messageBody: 'B-req-1' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'ERROR_RESPONSE', messageBody: 'A-fail-1' }),
      makeMessage({ exchangeId: 'B', endpointId: 'out-1', messageType: 'RESPONSE', messageBody: 'B-ok-1' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST', messageBody: 'A-req-2' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'RESPONSE', messageBody: 'A-ok-2' }),
    ];

    const interactions = buildInteractionsForEdge(messages, e);
    expect(interactions).toHaveLength(3);

    const aInteractions = interactions.filter((i) => i.exchangeId === 'A');
    expect(aInteractions).toHaveLength(2);
    expect(aInteractions[0]!.response?.messageBody).toBe('A-fail-1');
    expect(aInteractions[1]!.response?.messageBody).toBe('A-ok-2');

    const bInteractions = interactions.filter((i) => i.exchangeId === 'B');
    expect(bInteractions).toHaveLength(1);
    expect(bInteractions[0]!.response?.messageBody).toBe('B-ok-1');
  });
});
