import type { Message } from '@/types';

/**
 * One hop, as a bar on the waterfall: a SENDING paired with the SENT that closed it.
 *
 * Only SENT carries `timeTaken`, and Message stamps `timeStamp` when it is constructed - i.e. when
 * the hop finished. So the SENT's timestamp is the bar's END and `timeStamp - timeTaken` is its
 * start. Deriving it from the SENDING's own timestamp instead would be wrong for anything async:
 * a wireTap's SENDING is recorded on the calling thread, while the work happens elsewhere.
 *
 * That makes `start`/`end`/`durationMs` a self-consistent description of WHEN the hop ran
 * (`end - start === durationMs` always holds), and they are what the bar's position and width are
 * drawn from. They are deliberately NOT what row order is decided by - see {@link Span.seq}: these
 * are wall-clock samples, and a wall clock is too coarse to order hops that take under a
 * millisecond. Keeping the two concerns separate is why a bar can legitimately sit fractionally
 * left of the row above it; the rows are in causal order, the bars are on a time axis.
 */
export interface Span {
  exchangeId: string;
  /** null for a hop still in flight, or one whose response never arrived. */
  parentExchangeId: string | null;
  endpoint: string;
  routeId: string | null;
  /** Epoch ms. */
  start: number;
  /** Epoch ms. Equal to `start` for a hop with no recorded duration. */
  end: number;
  durationMs: number;
  isError: boolean;
  exception: string | null;
  /** Distance from the flow's root exchange; drives indentation. */
  depth: number;
  /** True when no SENT arrived, so the bar is a point rather than a measured span. */
  pending: boolean;
  /**
   * The position in the original `messages` array of this span's SENDING (or its SENT, when no
   * SENDING was recorded) - the order the server actually observed the hop OPEN in.
   *
   * This is what row order is sorted by, in place of `start`. The server appends traced messages in
   * event order (`debuggerStore` only ever concatenates a poll's results onto the tail), so this
   * index IS causal order, recorded rather than inferred - no clock arithmetic involved.
   *
   * Ordering on `start` instead cannot work, for two independent reasons:
   *
   * 1. Spans are paired per endpoint (see {@link buildSpansForExchange}), so a second visit to the
   *    same endpoint within one exchange - e.g. a routingSlip stop revisited later by a
   *    dynamicRouter - sits adjacent to the first visit in the pre-sort array rather than at its
   *    true position. Sorting by `start` is what was supposed to correct that, but hops finishing
   *    inside the same millisecond (routine for in-memory `direct:` calls) tie, and a stable sort
   *    then just preserves that wrong order.
   * 2. `start` is derived as `SENT.timeStamp - timeTaken` from `System.currentTimeMillis()`, whose
   *    resolution is coarse on Windows (~15ms ticks vs ~1ms elsewhere). A nested child can land in
   *    an earlier tick than the parent still waiting on it, computing an earlier `start` and
   *    rendering above its own caller.
   *
   * Deliberately keyed on the SENDING, not the SENT/anchor: for a nested pair on one exchange (e.g.
   * `direct:invokeMockC` wrapping `mock:C`) the inner hop's SENT always arrives first - the outer
   * cannot close until the inner does - so keying on the SENT would rank every child ahead of its
   * own parent. A hop's own SENDING always fires before any event its call produces, parent and
   * sibling alike, which is exactly the relation the waterfall needs to show.
   */
  seq: number;
  /**
   * The message the bar was built from - the SENT, or the SENDING when no response arrived.
   *
   * Carried so a span can be matched back to a topology edge with `matchMessageToEdge`, rather than
   * re-deriving the rules here. That matching is subtle (node id AND endpoint, error-handler edges
   * first) and having two copies of it would guarantee they drift.
   */
  message: Message;
}

/**
 * A root exchange and everything it spawned, ordered for display.
 *
 * Grouping is by `parentExchangeId` (see the Java-side `Message.parentExchangeId`), which is what
 * makes a wireTap/seda/multicast branch appear in the same waterfall as the exchange that spawned
 * it rather than as an unrelated island.
 */
export interface Flow {
  rootExchangeId: string;
  /**
   * The route the flow entered through - Camel's own `getFromRouteId()`, so a consumer route id
   * like `timerRoute` or `fileListenerRoute` for consumer-started traffic.
   *
   * Null when it cannot be determined. See {@link resolveFromRouteId} for why that happens.
   */
  fromRouteId: string | null;
  start: number;
  end: number;
  durationMs: number;
  hasError: boolean;
  spans: Span[];
}

const REQUEST_EVENTS = new Set(['SENDING']);
const RESPONSE_EVENTS = new Set(['SENT']);

/** `timeStamp` is epoch millis in a string; anything unparseable is treated as absent. */
function toEpochMs(value: string | null | undefined): number | null {
  if (!value) return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * Pair SENDING/SENT into spans. Pairing is per exchange AND endpoint, and a second SENDING for the
 * same pair opens a new span rather than overwriting - redeliveries reuse the exchange id for every
 * attempt, and each attempt is a bar of its own.
 */
function buildSpansForExchange(
  messages: Message[],
  seqOf: Map<Message, number>,
): Omit<Span, 'depth'>[] {
  type Open = { request: Message | null; response: Message | null };
  const byEndpoint = new Map<string, Open[]>();

  for (const m of messages) {
    if (!m.endpoint) continue;
    const key = m.endpoint;
    let pairs = byEndpoint.get(key);
    if (!pairs) {
      pairs = [];
      byEndpoint.set(key, pairs);
    }

    if (REQUEST_EVENTS.has(m.exchangeEventType)) {
      pairs.push({ request: m, response: null });
    } else if (RESPONSE_EVENTS.has(m.exchangeEventType)) {
      const open = [...pairs].reverse().find((p) => p.response === null);
      if (open) open.response = m;
      else pairs.push({ request: null, response: m });
    }
  }

  const spans: Omit<Span, 'depth'>[] = [];

  for (const pairs of byEndpoint.values()) {
    for (const { request, response } of pairs) {
      const anchor = response ?? request;
      if (!anchor) continue;

      const stamp = toEpochMs(anchor.timeStamp);
      if (stamp === null) continue;

      // Only a SENT carries a duration; a request-only bar is a point in time.
      const durationMs = response ? Math.max(0, response.timeTaken) : 0;

      spans.push({
        exchangeId: anchor.exchangeId,
        parentExchangeId: anchor.parentExchangeId ?? null,
        endpoint: anchor.endpoint ?? '(unknown)',
        routeId: anchor.routeId ?? request?.routeId ?? null,
        start: stamp - durationMs,
        end: stamp,
        durationMs,
        isError: response?.messageType === 'ERROR_RESPONSE',
        exception: response?.exception ?? null,
        pending: !response,
        seq: seqOf.get(request ?? anchor) ?? 0,
        message: anchor,
      });
    }
  }

  return spans;
}

/**
 * Resolve how deep an exchange sits in the parent chain, and which root it belongs to.
 *
 * A parent id pointing at an exchange that was never traced - the parent's messages fell outside
 * the timeline slice, or were evicted by the message cap - is treated as a root rather than
 * dropped, so a branch is never hidden just because its origin is missing.
 */
function resolveRoots(parentOf: Map<string, string | null>): {
  rootOf: Map<string, string>;
  depthOf: Map<string, number>;
} {
  const rootOf = new Map<string, string>();
  const depthOf = new Map<string, number>();

  for (const exchangeId of parentOf.keys()) {
    const chain: string[] = [];
    let cursor: string | undefined = exchangeId;
    const seen = new Set<string>();

    // walk up to the root, guarding against a cycle in malformed data
    while (cursor !== undefined && !seen.has(cursor)) {
      seen.add(cursor);
      chain.push(cursor);
      const parent = parentOf.get(cursor);
      if (!parent || !parentOf.has(parent)) break;
      cursor = parent;
    }

    // chain always holds at least the exchange itself, so the root is never missing
    const root = chain[chain.length - 1] ?? exchangeId;
    chain.forEach((id, indexFromSelf) => {
      const depth = chain.length - 1 - indexFromSelf;
      // keep the shallowest depth seen; an exchange is reached once, but be defensive
      if (!depthOf.has(id) || depth < depthOf.get(id)!) {
        depthOf.set(id, depth);
        rootOf.set(id, root);
      }
    });
  }

  return { rootOf, depthOf };
}

/**
 * Group traced messages into flows ready to render as a waterfall.
 *
 * Flows are returned newest-first, matching how the debugger surfaces recent traffic elsewhere.
 * Spans within a flow are ordered by the order the hops opened in - see {@link Span.seq} for why
 * that is recorded rather than derived from their timestamps.
 */
/**
 * Works out which route a flow entered through.
 *
 * Two sources, in order of authority:
 *
 * 1. The CREATED marker. The Java side puts `exchange.getFromRouteId()` on its `routeId`, which is
 *    exactly the consumer route. **Note this is not reachable from the debugger panel today**:
 *    `debuggerStore.applyFilter` keeps only SENDING/SENT, so the marker never arrives. It is kept
 *    first because it is the correct answer whenever a caller does have the full message list.
 * 2. The route the first send was made from. For a ROOT exchange this is the consumer route in
 *    practice - routing begins in the consumer, so its first `to(...)` is stamped with that route.
 *    Verified against a running sample: a timer-started exchange reports `timerRoute` from both
 *    sources.
 *
 * Null when neither is available, in which case the header simply omits the label.
 */
function resolveFromRouteId(rootMessages: Message[] | undefined): string | null {
  if (!rootMessages) return null;

  const created = rootMessages.find((m) => m.exchangeEventType === 'CREATED' && m.routeId);
  if (created?.routeId) return created.routeId;

  return rootMessages.find((m) => m.exchangeEventType === 'SENDING' && m.routeId)?.routeId ?? null;
}

export function buildFlows(messages: Message[]): Flow[] {
  const byExchange = new Map<string, Message[]>();
  const parentOf = new Map<string, string | null>();
  // true arrival order, used to break start-time ties deterministically - see Span.seq
  const seqOf = new Map<Message, number>();

  messages.forEach((m, index) => seqOf.set(m, index));

  for (const m of messages) {
    let list = byExchange.get(m.exchangeId);
    if (!list) {
      list = [];
      byExchange.set(m.exchangeId, list);
    }
    list.push(m);

    // every message of an exchange carries the same parent, but a CREATED marker may arrive
    // before the send that stamped it - so keep the first non-null we see
    const known = parentOf.get(m.exchangeId);
    if (!known) parentOf.set(m.exchangeId, m.parentExchangeId ?? null);
  }

  const { rootOf, depthOf } = resolveRoots(parentOf);

  const spansByRoot = new Map<string, Span[]>();

  for (const [exchangeId, msgs] of byExchange) {
    const root = rootOf.get(exchangeId) ?? exchangeId;
    const depth = depthOf.get(exchangeId) ?? 0;

    const spans = buildSpansForExchange(msgs, seqOf).map((s) => ({ ...s, depth }));
    if (spans.length === 0) continue;

    const existing = spansByRoot.get(root);
    if (existing) existing.push(...spans);
    else spansByRoot.set(root, spans);
  }

  const flows: Flow[] = [];

  for (const [rootExchangeId, spans] of spansByRoot) {
    // Causal order, not clock order - see Span.seq. Ordering on `start` instead would put row
    // order at the mercy of System.currentTimeMillis() arithmetic: hops that tie on the
    // millisecond (routine for in-memory direct: calls) fall back to whatever order the pairing
    // happened to produce, and on a coarse clock (Windows ticks ~15ms) a nested child can compute
    // an earlier start than the parent that is still waiting on it, rendering the child above its
    // own caller. seq is the order the server actually observed the hops open in, so it is immune
    // to both. It is a message array index and therefore already unique - nothing follows it.
    spans.sort((a, b) => a.seq - b.seq);

    const start = Math.min(...spans.map((s) => s.start));
    const end = Math.max(...spans.map((s) => s.end));

    flows.push({
      rootExchangeId,
      fromRouteId: resolveFromRouteId(byExchange.get(rootExchangeId)),
      start,
      end,
      durationMs: end - start,
      hasError: spans.some((s) => s.isError),
      spans,
    });
  }

  // newest first
  flows.sort((a, b) => b.start - a.start);

  return flows;
}

/** A bar narrower than this cannot be seen, and "too fast to measure" is a common, real result. */
const MIN_WIDTH_PCT = 0.75;

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

/**
 * Bar geometry as percentages of the flow's own span, for absolute positioning.
 *
 * A flow in which every hop reported 0 ms has no scale to lay out against. It is rendered as the
 * same minimal marker a 0 ms hop gets inside a measurable flow - deliberately NOT as a full-width
 * bar, which would read as "this took the whole window", the exact opposite of what happened. The
 * ms label carries the meaning; the bar only carries position.
 */
export function spanGeometry(span: Span, flow: Flow): { offsetPct: number; widthPct: number } {
  const total = flow.durationMs;

  if (total <= 0) {
    return { offsetPct: 0, widthPct: MIN_WIDTH_PCT };
  }

  const offsetPct = clamp(((span.start - flow.start) / total) * 100, 0, 100);
  const widthPct = Math.max(
    MIN_WIDTH_PCT,
    Math.min((span.durationMs / total) * 100, 100 - offsetPct),
  );

  return { offsetPct, widthPct };
}

/**
 * The rows one flow should actually draw, bounded but never hiding the slow hops.
 *
 * <p>A flow's span count is unbounded - a {@code split()} over a large body puts every item's
 * exchange in the same flow - so rendering all of them is not an option. Taking the first N alone is
 * not either: this panel exists to answer "why was this slow", and the one slow hop is as likely to
 * be item 3000 as item 3.
 *
 * <p>So the visible set is the union of the first {@code maxRows} and the {@code slowestCount}
 * slowest anywhere in the flow, kept in the flow's own order (see {@link Span.seq}) so the staircase
 * still reads top-to-bottom. An outlier in the tail therefore always appears, at its true position.
 *
 * @param flow          the flow to draw.
 * @param maxRows       how many leading spans to keep.
 * @param slowestCount  how many of the slowest spans to guarantee, wherever they fall.
 * @return the spans to render, in flow order; the whole flow when it is already small enough.
 */
export function visibleSpans(flow: Flow, maxRows: number, slowestCount: number): Span[] {
  if (flow.spans.length <= maxRows) {
    return flow.spans;
  }

  const keep = new Set<Span>(flow.spans.slice(0, maxRows));

  // only hops that actually took time are worth rescuing from the tail; pulling in 0ms rows would
  // just push out leading ones for nothing
  [...flow.spans]
    .filter((span) => span.durationMs > 0)
    .sort((a, b) => b.durationMs - a.durationMs)
    .slice(0, slowestCount)
    .forEach((span) => keep.add(span));

  return flow.spans.filter((span) => keep.has(span));
}
