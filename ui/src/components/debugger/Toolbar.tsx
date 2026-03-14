import { useState, useEffect, useRef } from 'react';
import type { CamelBeeContext } from '@/types';
import { useTraceStatus, useDeleteMessages } from '@/api';
import { useDebuggerStore } from '@/store/debuggerStore';

interface ToolbarProps {
  context: CamelBeeContext | undefined;
}

export function Toolbar({ context }: ToolbarProps) {
  const isTracing = useDebuggerStore((s) => s.isTracing);
  const setTracing = useDebuggerStore((s) => s.setTracing);
  const setFilterText = useDebuggerStore((s) => s.setFilterText);
  const filterText = useDebuggerStore((s) => s.filterText);
  const clearMessages = useDebuggerStore((s) => s.clearMessages);

  const [localFilter, setLocalFilter] = useState(filterText);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => {
    debounceRef.current = setTimeout(() => setFilterText(localFilter), 250);
    return () => clearTimeout(debounceRef.current);
  }, [localFilter, setFilterText]);

  const traceStatus = useTraceStatus();
  const deleteMessages = useDeleteMessages();

  const handleToggleTrace = () => {
    const next = !isTracing;
    if (next) {
      // Clear old messages before starting a new tracing session
      deleteMessages.mutate(undefined, {
        onSuccess: () => {
          clearMessages();
          traceStatus.mutate('ACTIVE', {
            onSuccess: () => setTracing(true),
          });
        },
      });
    } else {
      traceStatus.mutate('INACTIVE', {
        onSuccess: () => setTracing(false),
      });
    }
  };

  const handleDelete = () => {
    clearMessages();
    deleteMessages.mutate();
  };

  return (
    <div className="flex items-center gap-4 border-b border-gray-700 bg-gray-900 px-4 py-2">
      {/* Context info */}
      <div className="flex items-center gap-3 text-xs text-gray-400">
        {context && (
          <>
            <span className="font-semibold text-gray-200">{context.name}</span>
            <span>{context.framework}</span>
            <span>Camel {context.camelVersion}</span>
          </>
        )}
      </div>

      <div className="flex-1" />

      {/* Filter */}
      <input
        type="text"
        aria-label="Filter messages"
        placeholder="Filter messages…"
        value={localFilter}
        onChange={(e) => setLocalFilter(e.target.value)}
        className="w-48 rounded border border-gray-600 bg-gray-800 px-2 py-1 text-xs text-gray-200 placeholder-gray-500 focus:border-blue-500 focus:outline-none"
      />

      {/* Trace toggle */}
      <button
        onClick={handleToggleTrace}
        disabled={traceStatus.isPending}
        className={`rounded px-3 py-1 text-xs font-medium transition ${
          isTracing
            ? 'bg-red-600 hover:bg-red-700 text-white'
            : 'bg-green-600 hover:bg-green-700 text-white'
        } disabled:opacity-50`}
      >
        {isTracing ? 'Stop Tracing' : 'Start Tracing'}
      </button>

      {/* Delete messages */}
      <button
        onClick={handleDelete}
        disabled={deleteMessages.isPending}
        className="rounded bg-gray-700 px-3 py-1 text-xs font-medium text-gray-200 transition hover:bg-gray-600 disabled:opacity-50"
      >
        Clear
      </button>
    </div>
  );
}
