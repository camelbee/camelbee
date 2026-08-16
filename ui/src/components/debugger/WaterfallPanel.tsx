import { useEffect, useMemo, useRef, useState } from 'react';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useSettingsStore } from '@/store/settingsStore';
import { useDragResize } from '@/hooks/useDragResize';
import { matchMessageToEdge } from '@/utils/messageMatching';
import type { MessageEdge } from '@/utils/routeGraph';
import { buildFlows, spanGeometry, visibleSpans, type Flow, type Span } from '@/utils/waterfall';

/** How many flows to render at once. The panel is a recent-traffic view, not a log. */
const MAX_FLOWS = 50;

/**
 * Hard bound on the rows one flow may draw.
 *
 * <p>MAX_FLOWS alone does not bound the DOM, because the span count inside a single flow is
 * unbounded: a {@code split()} over a large body puts every item's exchange in ONE flow. Measured
 * at 10k traced messages of that shape - 5,001 spans in one flow, 25k DOM nodes, 540ms per render,
 * repeated on every 2s poll.
 */
export const MAX_SPANS_PER_FLOW = 200;

/**
 * On top of the leading rows, always show this many of the flow's slowest hops wherever they fall.
 * Without it the panel could hide the very hop the user opened it to find.
 */
export const ALWAYS_SHOW_SLOWEST = 10;

/**
 * Above this many flows, only the newest is expanded by default. Below it - the everyday case, a
 * handful of requests - everything stays open, which is what makes the panel useful at a glance.
 */
export const AUTO_COLLAPSE_ABOVE = 3;

/** Leave the graph above this much room however far the panel is dragged. */
const MIN_GRAPH_PX = 160;

interface WaterfallPanelProps {
  onClose: () => void;
  /** Topology edges, so a bar can be tied to the hop it represents on the graph. */
  edges?: MessageEdge[];
}

export function WaterfallPanel({ onClose, edges = [] }: WaterfallPanelProps) {
  const filteredMessages = useDebuggerStore((s) => s.filteredMessages);
  const timelineIndex = useDebuggerStore((s) => s.timelineIndex);

  // honour the timeline scrubber, so the waterfall shows the same slice as the graph
  const flows = useMemo(
    () => buildFlows(filteredMessages.slice(0, timelineIndex)).slice(0, MAX_FLOWS),
    [filteredMessages, timelineIndex],
  );

  const selectedEdgeId = useDebuggerStore((s) => s.selectedEdgeId);
  const selectEdge = useDebuggerStore((s) => s.selectEdge);

  /**
   * Which topology edge each span belongs to. Computed once per (flows, edges) rather than per row:
   * matching walks the edge list, and a flow can hold hundreds of spans.
   */
  const edgeIdOfSpan = useMemo(() => {
    const map = new Map<Span, string | null>();
    if (edges.length === 0) {
      return map;
    }
    for (const flow of flows) {
      for (const span of flow.spans) {
        map.set(span, matchMessageToEdge(span.message, edges)?.id ?? null);
      }
    }
    return map;
  }, [flows, edges]);

  /** Only flows the user explicitly toggled; everything else follows the default below. */
  const [overrides, setOverrides] = useState<Map<string, boolean>>(new Map());

  const isCollapsed = (flow: Flow, index: number) => {
    const explicit = overrides.get(flow.rootExchangeId);
    if (explicit !== undefined) {
      return explicit;
    }
    // selecting an edge on the graph must reveal its bars, not leave them behind a collapsed header
    if (selectedEdgeId && flow.spans.some((s) => edgeIdOfSpan.get(s) === selectedEdgeId)) {
      return false;
    }
    return flows.length > AUTO_COLLAPSE_ABOVE && index > 0;
  };

  const height = useSettingsStore((s) => s.waterfallHeight);
  const setHeight = useSettingsStore((s) => s.setWaterfallHeight);

  const { onMouseDown: onGripMouseDown, onKeyDown: onGripKeyDown } = useDragResize({
    size: height,
    setSize: setHeight,
    axis: 'vertical',
    minRemaining: MIN_GRAPH_PX,
  });

  const scrollAreaRef = useRef<HTMLDivElement>(null);

  /**
   * Which selection we have already scrolled to. Without it this would re-scroll on every render,
   * and the panel re-renders on every 2s message poll - it would fight the user's own scrolling for
   * as long as an edge stayed selected.
   */
  const scrolledForRef = useRef<string | null>(null);

  useEffect(() => {
    if (selectedEdgeId === scrolledForRef.current) {
      return;
    }
    scrolledForRef.current = selectedEdgeId;

    if (!selectedEdgeId) {
      return;
    }

    const target = scrollAreaRef.current?.querySelector('[data-testid="waterfall-row-selected"]');
    // 'nearest' so an already-visible row is left exactly where it is rather than jumping to centre
    target?.scrollIntoView?.({ block: 'nearest' });
  }, [selectedEdgeId, flows]);

  const toggle = (flow: Flow, index: number) =>
    setOverrides((prev) => {
      const next = new Map(prev);
      next.set(flow.rootExchangeId, !isCollapsed(flow, index));
      return next;
    });

  return (
    <div
      className="flex shrink-0 flex-col border-t border-gray-300 bg-white dark:border-gray-700 dark:bg-gray-900"
      style={{ height: `${height}px` }}
      data-testid="waterfall-panel"
    >
      {/* Resize grip. A separator rather than a button: it adjusts a boundary, it does not act. */}
      <div
        role="separator"
        aria-orientation="horizontal"
        aria-label="Resize waterfall"
        aria-valuenow={height}
        tabIndex={0}
        onMouseDown={onGripMouseDown}
        onKeyDown={onGripKeyDown}
        data-testid="waterfall-resize-handle"
        className="group -mt-1 flex h-2 w-full cursor-row-resize items-center justify-center focus:outline-none"
      >
        <span className="h-0.5 w-10 rounded-full bg-gray-300 group-hover:bg-blue-500 group-focus:bg-blue-500 dark:bg-gray-600" />
      </div>

      <div className="flex items-center justify-between border-b border-gray-300 px-3 py-1.5 dark:border-gray-700">
        <span className="text-xs font-semibold text-gray-800 dark:text-gray-200">
          Waterfall ({flows.length}
          {flows.length === MAX_FLOWS ? '+' : ''} flow{flows.length === 1 ? '' : 's'})
        </span>
        <button
          onClick={onClose}
          aria-label="Close waterfall"
          className="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200"
        >
          ✕
        </button>
      </div>

      <div ref={scrollAreaRef} className="flex-1 overflow-y-auto px-3 py-2">
        {flows.length === 0 ? (
          <p className="text-xs text-gray-400 dark:text-gray-500">
            No timed hops yet. Start tracing and send traffic through a route.
          </p>
        ) : (
          <div className="space-y-2">
            {flows.map((flow, index) => (
              <FlowRow
                key={flow.rootExchangeId}
                flow={flow}
                collapsed={isCollapsed(flow, index)}
                onToggle={() => toggle(flow, index)}
                edgeIdOfSpan={edgeIdOfSpan}
                selectedEdgeId={selectedEdgeId}
                onSelectEdge={selectEdge}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function FlowRow({
  flow,
  collapsed,
  onToggle,
  edgeIdOfSpan,
  selectedEdgeId,
  onSelectEdge,
}: {
  flow: Flow;
  collapsed: boolean;
  onToggle: () => void;
  edgeIdOfSpan: Map<Span, string | null>;
  selectedEdgeId: string | null;
  onSelectEdge: (edgeId: string | null) => void;
}) {
  const shown = visibleSpans(flow, MAX_SPANS_PER_FLOW, ALWAYS_SHOW_SLOWEST);
  const hidden = flow.spans.length - shown.length;

  return (
    <div className="rounded border border-gray-200 dark:border-gray-700">
      <button
        onClick={onToggle}
        aria-expanded={!collapsed}
        className="flex w-full items-center gap-2 px-2 py-1 text-left hover:bg-gray-100 dark:hover:bg-gray-800"
      >
        <span className="text-[10px] text-gray-400 dark:text-gray-500">
          {collapsed ? '▶' : '▼'}
        </span>
        <span
          className={`rounded px-1.5 py-0.5 text-[10px] font-bold uppercase ${
            flow.hasError
              ? 'bg-red-500/20 text-red-600 dark:text-red-400'
              : 'bg-green-500/20 text-green-600 dark:text-green-400'
          }`}
        >
          {flow.hasError ? 'Error' : 'OK'}
        </span>
        <span className="truncate text-[10px] text-gray-500 dark:text-gray-400">
          {flow.rootExchangeId}
        </span>
        <span className="ml-auto shrink-0 text-[10px] tabular-nums text-gray-500 dark:text-gray-400">
          {flow.spans.length} hop{flow.spans.length === 1 ? '' : 's'} · {flow.durationMs}ms
        </span>
      </button>

      {!collapsed && (
        <div className="space-y-0.5 px-2 pb-1.5">
          {shown.map((span, i) => {
            const edgeId = edgeIdOfSpan.get(span) ?? null;
            return (
              <SpanRow
                key={`${span.exchangeId}-${span.endpoint}-${i}`}
                span={span}
                flow={flow}
                edgeId={edgeId}
                selected={edgeId !== null && edgeId === selectedEdgeId}
                onSelect={onSelectEdge}
              />
            );
          })}
          {hidden > 0 && (
            <p className="pt-1 text-[10px] text-gray-400 dark:text-gray-500">
              + {hidden} more hops not shown. The slowest are always included, and the flow header
              counts and times all {flow.spans.length}.
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function SpanRow({
  span,
  flow,
  edgeId,
  selected,
  onSelect,
}: {
  span: Span;
  flow: Flow;
  edgeId: string | null;
  selected: boolean;
  onSelect: (edgeId: string | null) => void;
}) {
  const { offsetPct, widthPct } = spanGeometry(span, flow);

  const barClass = span.isError
    ? 'bg-red-500'
    : span.pending
      ? 'bg-gray-400 dark:bg-gray-500'
      : 'bg-blue-500';

  const title = [
    span.endpoint,
    span.routeId ? `route: ${span.routeId}` : null,
    span.pending ? 'no response recorded' : `${span.durationMs}ms`,
    span.exception,
  ]
    .filter(Boolean)
    .join('\n');

  // A hop with no matching edge stays inert rather than looking clickable and doing nothing. That
  // happens for the CREATED/COMPLETED-ish rows and for endpoints the static topology never declared.
  const selectable = edgeId !== null;

  return (
    <div
      role={selectable ? 'button' : undefined}
      tabIndex={selectable ? 0 : undefined}
      aria-pressed={selectable ? selected : undefined}
      onClick={selectable ? () => onSelect(selected ? null : edgeId) : undefined}
      onKeyDown={
        selectable
          ? (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelect(selected ? null : edgeId);
              }
            }
          : undefined
      }
      data-testid={selected ? 'waterfall-row-selected' : 'waterfall-row'}
      className={`flex items-center gap-2 rounded ${
        selectable ? 'cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800' : ''
      } ${selected ? 'bg-blue-500/15 ring-1 ring-blue-500/50' : ''}`}
      title={title}
    >
      <span
        className="w-40 shrink-0 truncate text-[10px] text-gray-600 dark:text-gray-300"
        style={{ paddingLeft: `${span.depth * 10}px` }}
      >
        {span.endpoint}
      </span>

      <div className="relative h-3 flex-1 rounded bg-gray-100 dark:bg-gray-800">
        <div
          data-testid="waterfall-bar"
          className={`absolute top-0 h-3 rounded ${barClass} ${span.pending ? 'opacity-60' : ''}`}
          style={{ left: `${offsetPct}%`, width: `${widthPct}%` }}
        />
      </div>

      <span className="w-14 shrink-0 text-right text-[10px] tabular-nums text-gray-500 dark:text-gray-400">
        {span.pending ? '—' : `${span.durationMs}ms`}
      </span>
    </div>
  );
}
