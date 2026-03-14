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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="mx-4 flex max-h-[80vh] w-full max-w-3xl flex-col overflow-hidden rounded-lg border border-gray-700 bg-gray-900 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-700 px-4 py-2">
          <h2 className="text-sm font-semibold text-gray-200">METRICS</h2>
          <div className="flex items-center gap-3">
            <input
              type="text"
              placeholder="Search..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-48 rounded border border-gray-600 bg-gray-800 px-2 py-1 text-xs text-gray-200 placeholder-gray-500 focus:border-blue-500 focus:outline-none"
            />
            <span className="text-[10px] text-gray-500">
              {filtered.length} / {metrics.length}
            </span>
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-200"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Content */}
        <pre className="flex-1 overflow-auto p-4 text-[11px] leading-relaxed text-gray-300">
          {text || 'No metrics available.'}
        </pre>
      </div>
    </div>
  );
}
