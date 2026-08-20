import { describe, it, expect } from 'vitest';
import { buildFlows, spanGeometry, visibleSpans } from './waterfall';
import { makeMessage } from '@/test/factories';

/** SENDING/SENT pair for one hop. `at` is when the hop finished; `took` its duration. */
function hop(
  exchangeId: string,
  endpoint: string,
  at: number,
  took: number,
  over: Partial<Parameters<typeof makeMessage>[0]> = {},
) {
  return [
    makeMessage({
      exchangeId,
      endpoint,
      exchangeEventType: 'SENDING',
      messageType: 'REQUEST',
      timeStamp: String(at - took),
      timeTaken: 0,
      ...over,
    }),
    makeMessage({
      exchangeId,
      endpoint,
      exchangeEventType: 'SENT',
      messageType: 'RESPONSE',
      timeStamp: String(at),
      timeTaken: took,
      ...over,
    }),
  ];
}

describe('buildFlows', () => {
  it('derives the bar from the SENT, whose timestamp is the END of the hop', () => {
    const flows = buildFlows(hop('ex-1', 'http://svc', 1000, 250));

    expect(flows).toHaveLength(1);
    const span = flows[0]!.spans[0]!;
    expect(span.end).toBe(1000);
    expect(span.start).toBe(750);
    expect(span.durationMs).toBe(250);
    expect(span.pending).toBe(false);
  });

  it('groups a child exchange into its parent flow instead of a separate island', () => {
    const messages = [
      ...hop('root', 'mock:before', 1000, 100),
      // a wireTap branch: different exchange id, linked by parentExchangeId
      ...hop('child', 'mock:tapped', 1200, 50, { parentExchangeId: 'root' }),
    ];

    const flows = buildFlows(messages);

    expect(flows).toHaveLength(1);
    expect(flows[0]!.rootExchangeId).toBe('root');
    expect(flows[0]!.spans.map((s) => s.exchangeId)).toEqual(['root', 'child']);
  });

  it('indents by distance from the root, so a copy of a copy nests one level deeper', () => {
    const messages = [
      ...hop('root', 'mock:a', 1000, 10),
      ...hop('child', 'mock:b', 1100, 10, { parentExchangeId: 'root' }),
      ...hop('grandchild', 'mock:c', 1200, 10, { parentExchangeId: 'child' }),
    ];

    const flows = buildFlows(messages);
    const depthOf = Object.fromEntries(flows[0]!.spans.map((s) => [s.exchangeId, s.depth]));

    expect(depthOf).toEqual({ root: 0, child: 1, grandchild: 2 });
  });

  it('keeps a branch whose parent was never traced, treating it as its own root', () => {
    // the parent's messages fell outside the timeline slice or were evicted by the cap
    const flows = buildFlows(hop('orphan', 'mock:x', 1000, 10, { parentExchangeId: 'long-gone' }));

    expect(flows).toHaveLength(1);
    expect(flows[0]!.rootExchangeId).toBe('orphan');
    expect(flows[0]!.spans[0]!.depth).toBe(0);
  });

  it('gives each redelivery attempt its own bar rather than overwriting', () => {
    // retries reuse the exchange id and the endpoint; each attempt is a separate hop
    const messages = [
      ...hop('ex-1', 'http://flaky', 1000, 100, { messageType: 'ERROR_RESPONSE' }),
      ...hop('ex-1', 'http://flaky', 1300, 120),
    ];

    const flows = buildFlows(messages);

    expect(flows[0]!.spans).toHaveLength(2);
    expect(flows[0]!.spans.map((s) => s.durationMs)).toEqual([100, 120]);
  });

  it('keeps true arrival order when a same-exchange endpoint is revisited and timestamps tie', () => {
    // e.g. a routingSlip stop revisited later by a dynamicRouter, on the exchange's own thread -
    // both hit direct:invokeMockC/mock:C, with direct:invokeMockD/mock:D truly in between. Fast
    // in-memory hops commonly tie on the millisecond, so this only surfaces via the seq tiebreak.
    const messages = [
      ...hop('ex-1', 'direct:invokeMockC', 1000, 0),
      ...hop('ex-1', 'mock:C', 1000, 0),
      ...hop('ex-1', 'direct:invokeMockD', 1000, 0),
      ...hop('ex-1', 'mock:D', 1000, 0),
      ...hop('ex-1', 'direct:invokeMockC', 1000, 0),
      ...hop('ex-1', 'mock:C', 1000, 0),
    ];

    const flows = buildFlows(messages);

    expect(flows[0]!.spans.map((s) => s.endpoint)).toEqual([
      'direct:invokeMockC',
      'mock:C',
      'direct:invokeMockD',
      'mock:D',
      'direct:invokeMockC',
      'mock:C',
    ]);
  });

  it('does not let a coarse SENT clock invert a synchronous parent above its own child', () => {
    // Windows' System.currentTimeMillis() ticks in ~15ms steps. A parent that truly closes 1ms
    // after its child can have its SENT land in the next tick, so the parent's derived start
    // (stamp - durationMs = 1009) comes out AFTER the child's (994) even though the parent opened
    // first and was still waiting on the child the whole time. Row order therefore cannot be taken
    // from `start` at all; it comes from the order the hops were observed to OPEN (Span.seq), which
    // no clock resolution can distort. The bars still sit on the timestamps - only the row order is
    // decided by seq - so this deliberately asserts order, not geometry.
    const messages = [
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'direct:invokeHttp',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '993',
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'http:health',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '994',
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'http:health',
        exchangeEventType: 'SENT',
        messageType: 'RESPONSE',
        timeStamp: '1000', // still the earlier clock tick
        timeTaken: 6,
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'direct:invokeHttp',
        exchangeEventType: 'SENT',
        messageType: 'RESPONSE',
        timeStamp: '1016', // jumped to the next ~15ms tick, though it truly closed ~1ms later
        timeTaken: 7,
      }),
    ];

    const flows = buildFlows(messages);

    expect(flows[0]!.spans.map((s) => s.endpoint)).toEqual(['direct:invokeHttp', 'http:health']);
  });

  it('orders a nested same-exchange pair by SENDING order, not by which SENT arrived first', () => {
    // direct:invokeMockC wraps mock:C on one exchange - the same shape as invokeHttp/health above,
    // but with timings taken from what the real sample actually reports for this pair: mock:C
    // closes at 1000 having taken 0ms (start 1000, end 1000); invokeMockC closes 1ms later at 1001
    // having taken 1ms (start 1000, end 1001). So their derived starts TIE, which is routine for
    // near-instant direct: hops and needs no clock quirk at all.
    //
    // Both of the obvious ways to break that tie are wrong, and this case catches both: the inner's
    // SENT always arrives before the outer's (the parent cannot close until its child has), so
    // ordering on SENT arrival ranks the child first; and a nested pair's `end` values structurally
    // never tie for the same reason, so a secondary sort on `end` also resolves in the child's
    // favour. Only the order the hops OPENED in (Span.seq, keyed on the SENDING) gets this right.
    const messages = [
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'direct:invokeMockC',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '1000',
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'mock:C',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '1000',
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'mock:C',
        exchangeEventType: 'SENT',
        messageType: 'RESPONSE',
        timeStamp: '1000', // inner closes first
        timeTaken: 0,
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'direct:invokeMockC',
        exchangeEventType: 'SENT',
        messageType: 'RESPONSE',
        timeStamp: '1001', // outer closes 1ms later, but computes the same start (1001 - 1 = 1000)
        timeTaken: 1,
      }),
    ];

    const flows = buildFlows(messages);

    expect(flows[0]!.spans.map((s) => s.endpoint)).toEqual(['direct:invokeMockC', 'mock:C']);
  });

  it('keeps a bar self-consistent: end - start is always exactly durationMs', () => {
    // The bar's position comes from `start` and its width from `durationMs`, so the two have to
    // describe the same interval or a bar is drawn somewhere it did not run. An earlier attempt at
    // the ordering fix clamped `start` back to the SENDING timestamp without touching `end` or
    // `durationMs`, which broke exactly this: an async hop (SENDING recorded on the calling thread
    // at 1000, work actually running 1450..1500) came out start=1000/end=1500/durationMs=50 and
    // rendered a 50ms bar 450ms to the left of the work it represented. Row order is Span.seq's
    // job; `start` must stay a truthful timestamp.
    const messages = [
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'direct:asyncThing',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '1000', // queued on the caller thread, long before the work runs
      }),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'direct:asyncThing',
        exchangeEventType: 'SENT',
        messageType: 'RESPONSE',
        timeStamp: '1500',
        timeTaken: 50,
      }),
      ...hop('ex-1', 'mock:sync', 1600, 20),
    ];

    const spans = buildFlows(messages)[0]!.spans;

    expect(spans).toHaveLength(2);
    spans.forEach((span) => {
      expect(span.end - span.start, `${span.endpoint} bar does not match its own duration`).toBe(
        span.durationMs,
      );
    });
    // and the async hop is placed at the work, not at the enqueue
    expect(spans.find((s) => s.endpoint === 'direct:asyncThing')!.start).toBe(1450);
  });

  it('marks a hop with no SENT as pending, with no invented duration', () => {
    const flows = buildFlows([
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'http://slow',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '1000',
        timeTaken: 0,
      }),
    ]);

    const span = flows[0]!.spans[0]!;
    expect(span.pending).toBe(true);
    expect(span.durationMs).toBe(0);
    expect(span.start).toBe(span.end);
  });

  it('flags the flow as errored when any hop failed, and carries the exception', () => {
    const messages = [
      ...hop('ex-1', 'mock:ok', 1000, 10),
      ...hop('ex-1', 'http://bad', 1100, 20, {
        messageType: 'ERROR_RESPONSE',
        exception: 'kaboom',
      }),
    ];

    const flows = buildFlows(messages);

    expect(flows[0]!.hasError).toBe(true);
    expect(flows[0]!.spans.find((s) => s.isError)?.exception).toBe('kaboom');
  });

  it('spans the flow from its earliest start to its latest end', () => {
    const messages = [
      ...hop('root', 'mock:a', 1000, 200),
      ...hop('child', 'mock:b', 1500, 100, { parentExchangeId: 'root' }),
    ];

    const flow = buildFlows(messages)[0]!;

    expect(flow.start).toBe(800);
    expect(flow.end).toBe(1500);
    expect(flow.durationMs).toBe(700);
  });

  it('orders flows newest first and spans oldest first within a flow', () => {
    const messages = [
      ...hop('old', 'mock:a', 1000, 10),
      ...hop('new', 'mock:b', 5000, 10),
      ...hop('new', 'mock:c', 5100, 10),
    ];

    const flows = buildFlows(messages);

    expect(flows.map((f) => f.rootExchangeId)).toEqual(['new', 'old']);
    expect(flows[0]!.spans.map((s) => s.endpoint)).toEqual(['mock:b', 'mock:c']);
  });

  it('ignores CREATED/COMPLETED markers, which describe the exchange and not a hop', () => {
    const flows = buildFlows([
      makeMessage({
        exchangeId: 'ex-1',
        exchangeEventType: 'CREATED',
        endpoint: null,
        timeStamp: '900',
      }),
      ...hop('ex-1', 'mock:a', 1000, 10),
      makeMessage({
        exchangeId: 'ex-1',
        exchangeEventType: 'COMPLETED',
        endpoint: 'someRoute',
        timeStamp: '1010',
      }),
    ]);

    expect(flows[0]!.spans).toHaveLength(1);
    expect(flows[0]!.spans[0]!.endpoint).toBe('mock:a');
  });

  it('drops a message whose timestamp is not parseable rather than placing it at epoch 0', () => {
    const flows = buildFlows([
      ...hop('ex-1', 'mock:a', 1000, 10),
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'mock:bad',
        exchangeEventType: 'SENT',
        timeStamp: 'not-a-number',
        timeTaken: 5,
      }),
    ]);

    expect(flows[0]!.spans.map((s) => s.endpoint)).toEqual(['mock:a']);
  });

  it('returns nothing for no messages', () => {
    expect(buildFlows([])).toEqual([]);
  });
});

describe('spanGeometry', () => {
  it('positions a bar proportionally within its flow', () => {
    const messages = [...hop('ex-1', 'mock:a', 1000, 100), ...hop('ex-1', 'mock:b', 1400, 200)];
    const flow = buildFlows(messages)[0]!;
    // flow spans 900..1400 = 500ms
    const second = flow.spans[1]!;

    const { offsetPct, widthPct } = spanGeometry(second, flow);

    expect(offsetPct).toBeCloseTo(60, 5); // starts at 1200, i.e. 300/500
    expect(widthPct).toBeCloseTo(40, 5); // 200/500
  });

  it('gives a zero-duration bar a visible floor instead of collapsing it', () => {
    const messages = [...hop('ex-1', 'mock:a', 1000, 500), ...hop('ex-1', 'mock:b', 1200, 0)];
    const flow = buildFlows(messages)[0]!;
    const instant = flow.spans.find((s) => s.durationMs === 0)!;

    expect(spanGeometry(instant, flow).widthPct).toBeGreaterThan(0);
  });

  it('does NOT fill the row when every hop was too fast to measure', () => {
    // a full-width bar would read as "this took the whole window", the opposite of the truth
    const flow = buildFlows(hop('ex-1', 'mock:a', 1000, 0))[0]!;
    const { offsetPct, widthPct } = spanGeometry(flow.spans[0]!, flow);

    expect(offsetPct).toBe(0);
    expect(widthPct).toBeGreaterThan(0);
    expect(widthPct).toBeLessThan(5);
  });
});

describe('visibleSpans', () => {
  /** A flow of `n` hops where hop `slowIndex` is by far the slowest. */
  function flowOf(n: number, slowIndex: number) {
    const messages = [];
    for (let i = 0; i < n; i++) {
      messages.push(...hop('ex-1', `mock://h${i}`, 1000 + i * 10, i === slowIndex ? 900 : 1));
    }
    return buildFlows(messages)[0]!;
  }

  it('returns the whole flow when it is already small enough', () => {
    const flow = flowOf(20, 3);

    expect(visibleSpans(flow, 100, 10)).toHaveLength(20);
    expect(visibleSpans(flow, 100, 10)).toEqual(flow.spans);
  });

  it('keeps the slowest hop even when it falls far past the row limit', () => {
    // the whole point: this panel answers "why was this slow", and the slow hop is as likely to be
    // item 300 as item 3
    const flow = flowOf(400, 300);
    const shown = visibleSpans(flow, 100, 10);

    expect(shown).toContain(flow.spans.find((s) => s.durationMs === 900));
  });

  it('stays bounded even so', () => {
    const flow = flowOf(400, 300);

    expect(visibleSpans(flow, 100, 10).length).toBeLessThanOrEqual(110);
  });

  it('keeps the leading hops, so the flow still reads from its beginning', () => {
    const flow = flowOf(400, 300);
    const shown = visibleSpans(flow, 100, 10);

    expect(shown.slice(0, 100)).toEqual(flow.spans.slice(0, 100));
  });

  it('returns spans in start order, so a rescued outlier sits at its real position', () => {
    const flow = flowOf(400, 300);
    const shown = visibleSpans(flow, 100, 10);

    const starts = shown.map((s) => s.start);
    expect([...starts].sort((a, b) => a - b)).toEqual(starts);
  });

  it('does not waste slots rescuing hops that took no measurable time', () => {
    // every hop 0ms except the leading ones; nothing in the tail is worth pulling forward
    const messages = [];
    for (let i = 0; i < 400; i++) {
      messages.push(...hop('ex-1', `mock://h${i}`, 1000 + i * 10, 0));
    }
    const flow = buildFlows(messages)[0]!;

    expect(visibleSpans(flow, 100, 10)).toHaveLength(100);
  });
});

describe('buildFlows - fromRouteId', () => {
  it('takes the consumer route from the CREATED marker', () => {
    // The Java side puts exchange.getFromRouteId() on CREATED.routeId, so this is the authority.
    const flows = buildFlows([
      makeMessage({
        exchangeId: 'ex-1',
        exchangeEventType: 'CREATED',
        routeId: 'timerRoute',
        endpoint: null,
        timeStamp: '1000',
      }),
      ...hop('ex-1', 'http://backend', 1200, 50, { routeId: 'invokeHttpRoute' }),
    ]);

    expect(flows).toHaveLength(1);
    // NOT invokeHttpRoute - that is where the send was made from, not where the flow entered.
    expect(flows[0]!.fromRouteId).toBe('timerRoute');
  });

  it('falls back to the first send when CREATED was never captured', () => {
    // Arming the tracer part-way through a flow loses its opening marker, and the message cap can
    // evict it. Verified against a running sample: exchanges traced mid-flight have no CREATED.
    const flows = buildFlows(hop('ex-2', 'http://backend', 1200, 50, { routeId: 'invokeHttpRoute' }));

    expect(flows[0]!.fromRouteId).toBe('invokeHttpRoute');
  });

  it('is null when no message carries a route at all', () => {
    const flows = buildFlows(hop('ex-3', 'mock:x', 1000, 5, { routeId: null }));

    expect(flows[0]!.fromRouteId).toBeNull();
  });

  it('reports the ROOT exchange route, not a branch route', () => {
    // A wireTap branch runs in a different route. The header describes the flow, so it has to name
    // where the flow entered - otherwise a branch's route would surface as the flow's origin.
    const flows = buildFlows([
      makeMessage({
        exchangeId: 'root',
        exchangeEventType: 'CREATED',
        routeId: 'fileListenerRoute',
        endpoint: null,
        timeStamp: '1000',
      }),
      ...hop('root', 'direct://tap', 1100, 10, { routeId: 'mainRoute' }),
      ...hop('child', 'mock:tapped', 1150, 20, {
        routeId: 'tappedRoute',
        parentExchangeId: 'root',
      }),
    ]);

    expect(flows).toHaveLength(1);
    expect(flows[0]!.fromRouteId).toBe('fileListenerRoute');
  });

  it('ignores a CREATED marker with no route and uses the send instead', () => {
    // getFromRouteId() is null for an exchange created by the platform-http producer - the Java
    // tracer documents exactly this case.
    const flows = buildFlows([
      makeMessage({
        exchangeId: 'ex-4',
        exchangeEventType: 'CREATED',
        routeId: null,
        endpoint: null,
        timeStamp: '1000',
      }),
      ...hop('ex-4', 'http://backend', 1100, 30, { routeId: 'restEntryRoute' }),
    ]);

    expect(flows[0]!.fromRouteId).toBe('restEntryRoute');
  });
});
