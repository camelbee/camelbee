import { useCallback, useEffect, useRef, useMemo, useState } from 'react';
import { useRoutes, useMessages, useHealth } from '@/api';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useSettingsStore } from '@/store/settingsStore';
import { Toolbar } from '@/components/debugger/Toolbar';
import { RouteGraph } from '@/components/debugger/RouteGraph';
import { TimelineBar } from '@/components/debugger/TimelineBar';
import { MessagePanel } from '@/components/debugger/MessagePanel';
import { buildRouteGraph, type MessageEdge } from '@/utils/routeGraph';

export function DebuggerPage() {
  const { data: context, isLoading, error } = useRoutes();
  const healthRefreshRate = useSettingsStore((s) => s.healthRefreshRate);
  const healthUrl = useSettingsStore((s) => s.healthUrl);
  const { data: health } = useHealth(true, healthRefreshRate, healthUrl);

  const isTracing = useDebuggerStore((s) => s.isTracing);
  const lastIndex = useDebuggerStore((s) => s.lastIndex);
  const addVersion = useDebuggerStore((s) => s.addVersion);
  const resetVersion = useDebuggerStore((s) => s.resetVersion);
  const appendMessages = useDebuggerStore((s) => s.appendMessages);
  const clearGeneration = useDebuggerStore((s) => s.clearGeneration);

  const messagesQuery = useMessages(lastIndex, addVersion, resetVersion, isTracing);

  // Track the previous data reference so we only process new data once
  const prevDataRef = useRef<unknown>(null);

  useEffect(() => {
    const data = messagesQuery.data;
    if (!data || data === prevDataRef.current) return;
    prevDataRef.current = data;

    if (data.messages.length > 0 || data.info.addVersion !== addVersion || data.info.resetVersion !== resetVersion) {
      appendMessages(data.messages, data.info.addVersion, data.info.resetVersion, data.info.capReached);
    }
  }, [messagesQuery.data, addVersion, resetVersion, appendMessages]);

  // Build static edges for the message panel
  const staticEdges = useMemo(() => {
    if (!context) return [];
    return buildRouteGraph(context).edges;
  }, [context]);

  // Track dynamic edges added by RouteGraph at runtime
  const [dynamicEdges, setDynamicEdges] = useState<MessageEdge[]>([]);

  const onDynamicEdgeAdded = useCallback((edge: MessageEdge) => {
    setDynamicEdges((prev) => {
      if (prev.some((e) => e.id === edge.id)) return prev;
      return [...prev, edge];
    });
  }, []);

  // Reset dynamic edges when the topology changes, and when the messages they were derived from
  // are cleared - otherwise the "N dynamic hops" badge keeps counting hops that no longer have
  // any messages behind them. RouteGraph drops the matching edges/nodes on the same signal.
  useEffect(() => {
    setDynamicEdges([]);
  }, [context, clearGeneration]);

  // Merge static + dynamic edges for MessagePanel
  const allEdges = useMemo(
    () => [...staticEdges, ...dynamicEdges],
    [staticEdges, dynamicEdges],
  );

  /**
   * Hops observed in traffic that the static topology did not predict. RouteGraph synthesizes an
   * edge for each of them so their messages are still reachable, which means nothing is lost - but
   * it also means a topology or matching gap leaves no trace. Surfacing the count makes it visible:
   * a non-zero value says the route model and the tracer disagree about how the routes connect.
   *
   * This is deliberately the already-tracked dynamic edge count rather than a re-match of every
   * message against every edge, which would be an O(messages x edges) scan on each poll.
   */
  const dynamicHopCount = dynamicEdges.length;

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <span className="text-sm text-gray-500 dark:text-gray-400">Loading routes…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <span className="text-sm text-red-600 dark:text-red-400">
          Failed to load routes: {(error as Error).message}
        </span>
      </div>
    );
  }

  return (
    <div className="relative flex flex-1 flex-col overflow-hidden">
      <Toolbar context={context} health={health ?? undefined} dynamicHopCount={dynamicHopCount} />

      <div className="flex flex-1 overflow-hidden">
        <div className="flex-1">
          {context && (
            <RouteGraph context={context} onDynamicEdgeAdded={onDynamicEdgeAdded} />
          )}
        </div>
        <MessagePanel edges={allEdges} />
      </div>

      <TimelineBar />
    </div>
  );
}