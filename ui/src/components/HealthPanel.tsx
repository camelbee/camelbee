import { useState } from 'react';
import type { HealthResponse } from '@/api';
import type { CamelBeeContext } from '@/types';

interface HealthPanelProps {
  context?: CamelBeeContext;
  health?: HealthResponse;
}

export function HealthPanel({ context, health }: HealthPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);

  const statusColor = health?.status === 'UP' ? 'bg-green-500' : health?.status === 'DOWN' ? 'bg-red-500' : 'bg-gray-500';

  return (
    <>
      {/* Collapsed indicator */}
      <div className="relative z-10 flex items-center">
        <button
          onClick={() => setExpanded(!expanded)}
          className={`flex h-5 w-5 items-center justify-center rounded-full ${statusColor} shadow-lg transition hover:scale-110`}
          title={health ? `Status: ${health.status}` : 'Health status'}
        >
          <span className="text-[9px] font-bold text-white">
            {health?.status === 'UP' ? '✓' : health?.status === 'DOWN' ? '✕' : '?'}
          </span>
        </button>

        {/* Expanded panel */}
        {expanded && (
          <div className="absolute left-0 top-full mt-2 w-72 rounded border border-gray-300 bg-white/95 p-3 shadow-xl backdrop-blur dark:border-gray-700 dark:bg-gray-900/95">
            <div className="space-y-1.5 text-[11px]">
              {health && (
                <div className="flex items-center gap-2">
                  <span className="text-gray-500 dark:text-gray-400">status</span>
                  <span className={health.status === 'UP' ? 'font-semibold text-green-600 dark:text-green-400' : 'font-semibold text-red-600 dark:text-red-400'}>
                    {health.status}
                  </span>
                </div>
              )}
              {context && (
                <>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-500 dark:text-gray-400">context name</span>
                    <span className="text-gray-800 dark:text-gray-200">{context.name}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-500 dark:text-gray-400">framework</span>
                    <span className="text-gray-800 dark:text-gray-200">{context.framework}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-500 dark:text-gray-400">camel</span>
                    <span className="text-gray-800 dark:text-gray-200">{context.camelVersion}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-500 dark:text-gray-400">jvm</span>
                    <span className="text-gray-800 dark:text-gray-200">{context.jvm}</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-gray-500 dark:text-gray-400">gc</span>
                    <span className="text-gray-800 dark:text-gray-200">{context.garbageCollectors}</span>
                  </div>
                </>
              )}
              {health && (
                <button
                  onClick={() => setDetailOpen(true)}
                  className="mt-2 w-full rounded bg-gray-200 px-2 py-1 text-[10px] text-gray-700 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                >
                  View Health Details
                </button>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Health detail modal */}
      {detailOpen && health && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 dark:bg-black/60">
          <div className="mx-4 max-h-[70vh] w-full max-w-2xl overflow-hidden rounded-lg border border-gray-300 bg-white shadow-2xl dark:border-gray-700 dark:bg-gray-900">
            <div className="flex items-center justify-between border-b border-gray-300 px-4 py-2 dark:border-gray-700">
              <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200">HEALTH</h2>
              <button
                onClick={() => setDetailOpen(false)}
                className="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200"
              >
                ✕
              </button>
            </div>
            <pre className="max-h-[60vh] overflow-auto p-4 text-xs text-gray-700 dark:text-gray-300">
              {JSON.stringify(health, null, 2)}
            </pre>
          </div>
        </div>
      )}
    </>
  );
}