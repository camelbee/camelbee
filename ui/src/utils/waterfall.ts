import type { Message } from '@/types';

/**
 * One hop, as a bar on the waterfall: a SENDING paired with the SENT that closed it.
 *
 * Only SENT carries `timeTaken`, and Message stamps `timeStamp` when it is constructed - i.e. when
 * the hop finished. So the SENT's timestamp is the bar's END and `timeStamp - timeTaken` is its
 * start. Deriving it from the SENDING's own timestamp instead would be wrong for anything async:
 * a wireTap's SENDING is recorded on the calling thread, while the work happens elsewhere.
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
function buildSpansForExchange(messages: Message[]): Omit<Span, 'depth'>[] {
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
 * Spans within a flow are ordered by start time so the staircase reads top-to-bottom.
 */
export function buildFlows(messages: Message[]): Flow[] {
  const byExchange = new Map<string, Message[]>();
  const parentOf = new Map<string, string | null>();

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

    const spans = buildSpansForExchange(msgs).map((s) => ({ ...s, depth }));
    if (spans.length === 0) continue;

    const existing = spansByRoot.get(root);
    if (existing) existing.push(...spans);
    else spansByRoot.set(root, spans);
  }

  const flows: Flow[] = [];

  for (const [rootExchangeId, spans] of spansByRoot) {
    spans.sort((a, b) => a.start - b.start || a.end - b.end);

    const start = Math.min(...spans.map((s) => s.start));
    const end = Math.max(...spans.map((s) => s.end));

    flows.push({
      rootExchangeId,
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
 * exchange in the same flow - so rendering all of them is not an option. Taking the first N by start
 * time alone is not either: this panel exists to answer "why was this slow", and the one slow hop is
 * as likely to be item 3000 as item 3.
 *
 * <p>So the visible set is the union of the first {@code maxRows} in time order and the
 * {@code slowestCount} slowest anywhere in the flow, re-sorted by start time so the staircase still
 * reads left to right. An outlier in the tail therefore always appears, at its true position.
 *
 * @param flow          the flow to draw.
 * @param maxRows       how many leading spans to keep.
 * @param slowestCount  how many of the slowest spans to guarantee, wherever they fall.
 * @return the spans to render, in start order; the whole flow when it is already small enough.
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
