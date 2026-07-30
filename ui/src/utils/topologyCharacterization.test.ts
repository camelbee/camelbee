/**
 * T0 — Characterization suite for the route-topology graph builder and
 * message→edge matching (see README-camel421-notes.md, FINAL ROADMAP v2).
 *
 * These tests pin the CURRENT behavior of buildRouteGraph and the matching
 * utilities against one realistic fixture — including KNOWN BUGS, which are
 * asserted as they behave today and labeled `KNOWN BUG (#n)` with their
 * roadmap item number. When an item (e.g. #3 query-param matching, #18
 * loop-safe interactions) is implemented, the corresponding assertions here
 * MUST be updated in the same change — the diff in this file is the proof
 * that exactly the intended behavior changed and nothing else.
 *
 * Output `description` strings follow Camel's toString() formats:
 * `To[…]`/`to[…]` (case varies by Camel version — matching is
 * case-insensitive), `DynamicTo[toD[…]]`, `WireTap[…]`, `Enrich[…{…}…]`,
 * `RecipientList[…{a,b}…]`, `RoutingSlip[…{a,b}…]`.
 */
import { describe, it, expect } from 'vitest';
import type { CamelBeeContext, CamelRouteOutput, Message } from '@/types';
import { buildRouteGraph, makeNodeId, makeProducerId } from './routeGraph';
import { matchMessageToEdge, buildInteractionsForEdge } from './messageMatching';

/* ------------------------------------------------------------------ */
/*  Fixture — one payload exercising every extraction/matching path   */
/* ------------------------------------------------------------------ */

const output = (
  id: string,
  description: string,
  type: string,
  delimiter: string | null = null,
): CamelRouteOutput => ({
  id,
  description,
  delimiter,
  type,
  outputs: [],
});

const TO = 'org.apache.camel.model.ToDefinition';
const TOD = 'org.apache.camel.model.ToDynamicDefinition';
const WIRETAP = 'org.apache.camel.model.WireTapDefinition';
const ENRICH = 'org.apache.camel.model.EnrichDefinition';
const RL = 'org.apache.camel.model.RecipientListDefinition';
const RS = 'org.apache.camel.model.RoutingSlipDefinition';

const KAFKA_URI = 'kafka:orders?requestTimeout=5000';

const fixture: CamelBeeContext = {
  name: 'characterization-fixture',
  jvm: 'test',
  jvmInputParameters: '',
  garbageCollectors: '',
  framework: 'test',
  camelVersion: '4.21.0',
  routes: [
    {
      id: 'restOrder',
      input:
        'From[rest://post:/api/v1/order?consumerComponentName=platform-http&routeId=restOrder]',
      rest: true,
      errorHandler: null,
      outputs: [
        // The real backend serializes the nested `outputs` field as null —
        // pin that the builder tolerates it (defensive flattenOutputs).
        {
          ...output('to-rest-1', 'To[direct:processOrder]', TO),
          outputs: null as unknown as CamelRouteOutput[],
        },
      ],
    },
    {
      id: 'processOrder',
      input: 'From[direct:processOrder]',
      rest: false,
      errorHandler: 'direct:orderErrors',
      outputs: [
        output('enrich-1', 'Enrich[constant{direct:enrichPrices}]', ENRICH),
        output('wiretap-1', 'WireTap[direct:auditTap]', WIRETAP),
        output('to-kafka-1', `To[${KAFKA_URI}]`, TO),
        output('tod-1', 'DynamicTo[toD[seda:dispatch]]', TOD),
        // FIXED (#3): query param on a direct: target now matches correctly.
        output('to-qp-1', 'To[direct:notify?block=false]', TO),
      ],
    },
    {
      id: 'enrichPrices',
      input: 'From[direct:enrichPrices]',
      rest: false,
      errorHandler: null,
      outputs: [output('to-http-1', 'To[http://pricing.svc/quote]', TO)],
    },
    {
      id: 'auditTap',
      input: 'From[direct:auditTap]',
      rest: false,
      errorHandler: null,
      // KNOWN LIMITATION (#5): log: is treated as an external system.
      outputs: [output('to-log-1', 'To[log:audit]', TO)],
    },
    {
      id: 'dispatch',
      input: 'From[seda:dispatch]',
      rest: false,
      errorHandler: null,
      outputs: [
        output(
          'rl-1',
          'RecipientList[constant{direct:shipA,direct:shipB}]',
          RL,
          ',',
        ),
      ],
    },
    {
      id: 'shipA',
      input: 'From[direct:shipA]',
      rest: false,
      errorHandler: null,
      outputs: [
        output('rs-1', 'RoutingSlip[constant{direct:shipB,mock:ship}]', RS, ','),
      ],
    },
    {
      id: 'shipB',
      input: 'From[direct:shipB]',
      rest: false,
      errorHandler: null,
      outputs: [],
    },
    {
      id: 'notify',
      input: 'From[direct:notify]',
      rest: false,
      errorHandler: null,
      outputs: [],
    },
    {
      // Auto-generated route id → label falls back to the input URI.
      id: 'route1',
      input: 'From[timer:tick?period=5000]',
      rest: false,
      errorHandler: null,
      // FIXED (#3): query param on a seda: target now correctly resolves
      // to an edge (still no producer node — it's internal, not external).
      outputs: [output('to-seda-miss', 'To[seda:dispatch?size=100]', TO)],
    },
    {
      id: 'orderErrors',
      input: 'From[direct:orderErrors]',
      rest: false,
      errorHandler: null,
      outputs: [output('to-mock-err', 'To[mock:errors]', TO)],
    },
  ],
};

/* ------------------------------------------------------------------ */
/*  Route topology characterization                                   */
/* ------------------------------------------------------------------ */

describe('T0: buildRouteGraph characterization', () => {
  const { nodes, edges } = buildRouteGraph(fixture);

  const nodeById = new Map(nodes.map((n) => [n.id, n]));
  const kindOf = (id: string) => nodeById.get(id)?.data.kind;

  it('produces the exact node set', () => {
    const expectedIds = [
      // route nodes
      makeNodeId('restOrder'),
      makeNodeId('processOrder'),
      makeNodeId('enrichPrices'),
      makeNodeId('auditTap'),
      makeNodeId('dispatch'),
      makeNodeId('shipA'),
      makeNodeId('shipB'),
      makeNodeId('notify'),
      makeNodeId('route1'),
      makeNodeId('orderErrors'),
      // producer nodes (external systems)
      makeProducerId('http://pricing.svc/quote'),
      makeProducerId('log:audit'),
      makeProducerId(KAFKA_URI),
      makeProducerId('mock:ship'),
      makeProducerId('mock:errors'),
    ];
    expect(nodes.map((n) => n.id).sort()).toEqual([...expectedIds].sort());
  });

  it('classifies node kinds (consumer / internal / producer)', () => {
    expect(kindOf(makeNodeId('restOrder'))).toBe('consumer'); // rest=true
    expect(kindOf(makeNodeId('route1'))).toBe('consumer'); // timer input
    expect(kindOf(makeNodeId('processOrder'))).toBe('internal');
    expect(kindOf(makeNodeId('enrichPrices'))).toBe('internal');
    expect(kindOf(makeNodeId('auditTap'))).toBe('internal');
    expect(kindOf(makeNodeId('dispatch'))).toBe('internal');
    expect(kindOf(makeNodeId('shipA'))).toBe('internal');
    expect(kindOf(makeNodeId('shipB'))).toBe('internal');
    for (const uri of [
      'http://pricing.svc/quote',
      'log:audit',
      KAFKA_URI,
      'mock:ship',
      'mock:errors',
    ]) {
      expect(kindOf(makeProducerId(uri))).toBe('producer');
    }
  });

  it('FIXED (#3): a route referenced only with query params is correctly linked, not a disconnected consumer', () => {
    // processOrder calls To[direct:notify?block=false]; query-param-proof
    // matching now recognizes it, so `notify` is correctly classified as
    // internal (called by another route), with a real edge into it.
    expect(kindOf(makeNodeId('notify'))).toBe('internal');
    const notifyEdges = edges.filter(
      (e) =>
        e.source === makeNodeId('notify') || e.target === makeNodeId('notify'),
    );
    expect(notifyEdges).toHaveLength(1);
    expect(notifyEdges[0]!.data!.outputId).toBe('to-qp-1');
  });

  it('CURRENT BEHAVIOR: an error-handler target route drawn in the orphan pass stays kind=internal, not error', () => {
    // Pass 2 draws orderErrors as 'internal' before Pass 3 tries 'error'.
    expect(kindOf(makeNodeId('orderErrors'))).toBe('internal');
  });

  it('produces the exact edge set (source → target, outputId)', () => {
    const actual = edges
      .map((e) => ({
        source: e.source,
        target: e.target,
        outputId: e.data!.outputId,
        isErrorHandler: e.data!.isErrorHandler,
      }))
      .sort((a, b) =>
        `${a.source}|${a.target}|${a.outputId}`.localeCompare(
          `${b.source}|${b.target}|${b.outputId}`,
        ),
      );

    const expected = [
      { s: makeNodeId('restOrder'), t: makeNodeId('processOrder'), o: 'to-rest-1', eh: false },
      { s: makeNodeId('processOrder'), t: makeNodeId('enrichPrices'), o: 'enrich-1', eh: false },
      { s: makeNodeId('processOrder'), t: makeNodeId('auditTap'), o: 'wiretap-1', eh: false },
      { s: makeNodeId('processOrder'), t: makeProducerId(KAFKA_URI), o: 'to-kafka-1', eh: false },
      { s: makeNodeId('processOrder'), t: makeNodeId('dispatch'), o: 'tod-1', eh: false },
      { s: makeNodeId('processOrder'), t: makeNodeId('orderErrors'), o: 'errorHandler-processOrder', eh: true },
      { s: makeNodeId('enrichPrices'), t: makeProducerId('http://pricing.svc/quote'), o: 'to-http-1', eh: false },
      { s: makeNodeId('auditTap'), t: makeProducerId('log:audit'), o: 'to-log-1', eh: false },
      { s: makeNodeId('dispatch'), t: makeNodeId('shipA'), o: 'rl-1', eh: false },
      { s: makeNodeId('dispatch'), t: makeNodeId('shipB'), o: 'rl-1', eh: false },
      { s: makeNodeId('shipA'), t: makeNodeId('shipB'), o: 'rs-1', eh: false },
      { s: makeNodeId('shipA'), t: makeProducerId('mock:ship'), o: 'rs-1', eh: false },
      { s: makeNodeId('orderErrors'), t: makeProducerId('mock:errors'), o: 'to-mock-err', eh: false },
      // FIXED (#3): these two edges were missing before query-param-proof matching.
      { s: makeNodeId('processOrder'), t: makeNodeId('notify'), o: 'to-qp-1', eh: false },
      { s: makeNodeId('route1'), t: makeNodeId('dispatch'), o: 'to-seda-miss', eh: false },
    ]
      .map((e) => ({
        source: e.s,
        target: e.t,
        outputId: e.o,
        isErrorHandler: e.eh,
      }))
      .sort((a, b) =>
        `${a.source}|${a.target}|${a.outputId}`.localeCompare(
          `${b.source}|${b.target}|${b.outputId}`,
        ),
      );

    expect(actual).toEqual(expected);
  });

  it('FIXED (#3): To[seda:dispatch?size=100] resolves to a real edge, not a phantom producer', () => {
    // An edge to `dispatch` now exists; still no producer node (it's internal).
    expect(edges.some((e) => e.data!.outputId === 'to-seda-miss')).toBe(true);
    expect(nodeById.has(makeProducerId('seda:dispatch?size=100'))).toBe(false);
  });

  it('resolves node labels (REST prefix, auto-id fallback to input URI)', () => {
    expect(nodeById.get(makeNodeId('restOrder'))!.data.label).toBe(
      'REST restOrder',
    );
    expect(nodeById.get(makeNodeId('route1'))!.data.label).toBe(
      'timer:tick?period=5000',
    );
    expect(nodeById.get(makeNodeId('processOrder'))!.data.label).toBe(
      'processOrder',
    );
  });

  it('populates edge matching metadata (input URIs for the tracer fallback)', () => {
    const kafkaEdge = edges.find((e) => e.data!.outputId === 'to-kafka-1')!;
    expect(kafkaEdge.data!.sourceRouteId).toBe('processOrder');
    expect(kafkaEdge.data!.sourceInputUri).toBe('direct:processOrder');
    expect(kafkaEdge.data!.targetUri).toBe(KAFKA_URI);

    const todEdge = edges.find((e) => e.data!.outputId === 'tod-1')!;
    expect(todEdge.data!.targetRouteId).toBe('dispatch');
    expect(todEdge.data!.targetInputUri).toBe('seda:dispatch');
  });
});

/* ------------------------------------------------------------------ */
/*  Message tracing characterization (same graph)                     */
/* ------------------------------------------------------------------ */

const msg = (over: Partial<Message>): Message => ({
  exchangeId: 'ex-1',
  exchangeEventType: 'SENDING',
  messageBody: null,
  headers: null,
  routeId: 'processOrder',
  endpoint: KAFKA_URI,
  endpointId: 'to-kafka-1',
  messageType: 'REQUEST',
  exception: null,
  timeStamp: '2026-07-29T10:00:00Z',
  timeTaken: 0,
  ...over,
});

describe('T0: message → edge matching characterization', () => {
  const { edges } = buildRouteGraph(fixture);
  const edgeByOutput = (outputId: string) =>
    edges.find((e) => e.data!.outputId === outputId)!;

  it('primary match: endpointId → outputId', () => {
    const matched = matchMessageToEdge(msg({}), edges);
    expect(matched).toBe(edgeByOutput('to-kafka-1'));
  });

  it('fallback match: routeId + endpoint vs target input URI', () => {
    const matched = matchMessageToEdge(
      msg({ endpointId: 'not-a-known-output', endpoint: 'seda:dispatch' }),
      edges,
    );
    expect(matched).toBe(edgeByOutput('tod-1'));
  });

  it('fallback match: tracer reports input URI as routeId', () => {
    const matched = matchMessageToEdge(
      msg({
        routeId: 'direct:enrichPrices',
        endpointId: 'nope',
        endpoint: 'http://pricing.svc/quote',
      }),
      edges,
    );
    expect(matched).toBe(edgeByOutput('to-http-1'));
  });

  it('error-handler edge match: endpoint = target route id', () => {
    const matched = matchMessageToEdge(
      msg({ endpointId: '', endpoint: 'orderErrors' }),
      edges,
    );
    expect(matched).toBe(edgeByOutput('errorHandler-processOrder'));
  });

  it('unmatched dynamic endpoint returns null (feeds createDynamicEdge)', () => {
    const matched = matchMessageToEdge(
      msg({ endpointId: 'dyn-1', endpoint: 'sql:select-now' }),
      edges,
    );
    expect(matched).toBeNull();
  });
});

describe('T0: interaction building characterization', () => {
  const { edges } = buildRouteGraph(fixture);
  const kafkaEdge = edges.find((e) => e.data!.outputId === 'to-kafka-1')!;

  it('pairs request/response per exchangeId and flags errors', () => {
    const messages: Message[] = [
      msg({ exchangeId: 'ex-1', messageBody: 'req-1' }),
      msg({
        exchangeId: 'ex-1',
        exchangeEventType: 'SENT',
        messageType: 'RESPONSE',
        messageBody: 'rsp-1',
      }),
      msg({ exchangeId: 'ex-2', messageBody: 'req-2' }),
      msg({ exchangeId: 'ex-3', messageBody: 'req-3' }),
      msg({
        exchangeId: 'ex-3',
        exchangeEventType: 'SENT',
        messageType: 'ERROR_RESPONSE',
        exception: 'boom',
      }),
    ];

    const interactions = buildInteractionsForEdge(messages, kafkaEdge);
    expect(interactions).toHaveLength(3);

    const byId = new Map(interactions.map((i) => [i.exchangeId, i]));
    expect(byId.get('ex-1')!.request?.messageBody).toBe('req-1');
    expect(byId.get('ex-1')!.response?.messageBody).toBe('rsp-1');
    expect(byId.get('ex-1')!.isError).toBe(false);
    expect(byId.get('ex-2')!.response).toBeNull();
    expect(byId.get('ex-3')!.isError).toBe(true);
  });

  it('FIXED (#18): a retried request on the same edge+exchange is kept as its own interaction', () => {
    const messages: Message[] = [
      msg({ exchangeId: 'ex-1', messageBody: 'first-attempt' }),
      msg({ exchangeId: 'ex-1', messageBody: 'second-attempt' }),
    ];

    const interactions = buildInteractionsForEdge(messages, kafkaEdge);
    // Loops/redeliveries no longer collapse — each REQUEST on the same
    // exchangeId starts a new pair (roadmap #18).
    expect(interactions).toHaveLength(2);
    expect(interactions[0]!.request?.messageBody).toBe('first-attempt');
    expect(interactions[1]!.request?.messageBody).toBe('second-attempt');
  });
});
