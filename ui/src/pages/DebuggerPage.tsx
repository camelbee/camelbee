import { useEffect, useRef, useMemo } from 'react';
import { useRoutes, useMessages, useHealth } from '@/api';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useSettingsStore } from '@/store/settingsStore';
import { Toolbar } from '@/components/debugger/Toolbar';
import { RouteGraph } from '@/components/debugger/RouteGraph';
import { TimelineBar } from '@/components/debugger/TimelineBar';
import { MessagePanel } from '@/components/debugger/MessagePanel';
import { HealthPanel } from '@/components/HealthPanel';
import { buildRouteGraph } from '@/utils/routeGraph';

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

  // Build edges for the message panel
  const graphEdges = useMemo(() => {
    if (!context) return [];
    return buildRouteGraph(context).edges;
  }, [context]);

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <span className="text-sm text-gray-400">Loading routes…</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <span className="text-sm text-red-400">
          Failed to load routes: {(error as Error).message}
        </span>
      </div>
    );
  }

  return (
    <div className="relative flex flex-1 flex-col overflow-hidden">
      <HealthPanel context={context} health={health ?? undefined} />
      <Toolbar context={context} />

      <div className="flex flex-1 overflow-hidden">
        <div className="flex-1">
          {context && <RouteGraph context={context} />}
        </div>
        <MessagePanel edges={graphEdges} />
      </div>

      <TimelineBar />
    </div>
  );
}
