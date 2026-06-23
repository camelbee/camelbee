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
});
