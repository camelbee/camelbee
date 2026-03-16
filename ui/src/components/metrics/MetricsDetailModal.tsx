import { useState, useMemo } from 'react';
import type { PrometheusMetric } from '@/types';

interface MetricsDetailModalProps {
  metrics: PrometheusMetric[];
  onClose: () => void;
}

export function MetricsDetailModal({ metrics, onClose }: MetricsDetailModalProps) {
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    if (!search) return metrics;
    const lower = search.toLowerCase();
    return metrics.filter((m) => m.name.toLowerCase().includes(lower));
  }, [metrics, search]);

  const text = useMemo(
    () => filtered.map((m) => `${m.name} ${m.value}`).join('\n'),
    [filtered],
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 dark:bg-black/60">
      <div className="mx-4 flex max-h-[80vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-gray-300 bg-white shadow-2xl dark:border-gray-700 dark:bg-gray-900">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-300 px-4 py-2 dark:border-gray-700">
          <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200">METRICS</h2>
          <div className="flex items-center gap-3">
            <input
              type="text"
              placeholder="Search..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-48 rounded border border-gray-300 bg-gray-50 px-2 py-1 text-xs text-gray-800 placeholder-gray-400 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:placeholder-gray-500"
            />
            <span className="text-[10px] text-gray-400 dark:text-gray-500">
              {filtered.length} / {metrics.length}
            </span>
            <button
              onClick={onClose}
              className="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Content */}
        <pre className="flex-1 overflow-auto p-4 text-[11px] leading-relaxed text-gray-700 dark:text-gray-300">
          {text || 'No metrics available.'}
        </pre>
      </div>
    </div>
  );
}