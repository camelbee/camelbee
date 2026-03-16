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

  const messagesQuery = useMessages(lastIndex, addVersion, resetVersion, isTracing);

  // Track the previous data reference so we only process new data once
  const prevDataRef = useRef<unknown>(null);

  useEffect(() => {
    const data = messagesQuery.data;
    if (!data || data === prevDataRef.current) return;
    prevDataRef.current = data;

    if (data.messages.length > 0 || data.info.addVersion !== addVersion || data.info.resetVersion !== resetVersion) {
      appendMessages(data.messages, data.info.addVersion, data.info.resetVersion);
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

  // Reset dynamic edges when context changes
  useEffect(() => {
    setDynamicEdges([]);
  }, [context]);

  // Merge static + dynamic edges for MessagePanel
  const allEdges = useMemo(
    () => [...staticEdges, ...dynamicEdges],
    [staticEdges, dynamicEdges],
  );

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
      <Toolbar context={context} health={health ?? undefined} />

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