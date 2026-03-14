import { useEffect, useState } from 'react';
import { useRoutes, useMetrics, useHealth } from '@/api';
import { useSettingsStore } from '@/store/settingsStore';
import { useMetricsStore } from '@/store/metricsStore';
import { HealthPanel } from '@/components/HealthPanel';
import { MetricsRouteGraph } from '@/components/metrics/MetricsRouteGraph';
import { MetricsCharts } from '@/components/metrics/MetricsCharts';
import { MetricsDetailModal } from '@/components/metrics/MetricsDetailModal';

type View = 'topology' | 'charts';

export function MetricsPage() {
  const { data: context, isLoading, error } = useRoutes();
  const healthRefreshRate = useSettingsStore((s) => s.healthRefreshRate);
  const healthUrl = useSettingsStore((s) => s.healthUrl);
  const metricsRefreshRate = useSettingsStore((s) => s.metricsRefreshRate);
  const metricsUrl = useSettingsStore((s) => s.metricsUrl);
  const { data: health } = useHealth(true, healthRefreshRate, healthUrl);
  const { data: metricsData } = useMetrics(true, metricsRefreshRate, metricsUrl);

  const updateFromPrometheus = useMetricsStore((s) => s.updateFromPrometheus);
  const rawMetrics = useMetricsStore((s) => s.rawMetrics);

  const [view, setView] = useState<View>('topology');
  const [detailOpen, setDetailOpen] = useState(false);

  // Feed metrics data into store
  useEffect(() => {
    if (metricsData && metricsData.length > 0) {
      updateFromPrometheus(metricsData);
    }
  }, [metricsData, updateFromPrometheus]);

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
      {/* Health panel overlay */}
      <HealthPanel context={context} health={health ?? undefined} />

      {/* Top bar */}
      <div className="flex items-center justify-between border-b border-gray-700 bg-gray-900 px-4 py-2">
        <div className="flex items-center gap-3 text-xs text-gray-400">
          {context && (
            <>
              <span className="font-semibold text-gray-200">{context.name}</span>
              <span>{context.framework}</span>
              <span>Camel {context.camelVersion}</span>
            </>
          )}
        </div>

        <div className="flex items-center gap-3">
          {/* View toggle */}
          <div className="flex rounded border border-gray-600">
            <button
              onClick={() => setView('topology')}
              className={`px-3 py-1 text-xs font-medium transition ${
                view === 'topology'
                  ? 'bg-gray-700 text-white'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              Topology
            </button>
            <button
              onClick={() => setView('charts')}
              className={`px-3 py-1 text-xs font-medium transition ${
                view === 'charts'
                  ? 'bg-gray-700 text-white'
                  : 'text-gray-400 hover:text-gray-200'
              }`}
            >
              Charts
            </button>
          </div>

          {/* Show all metrics */}
          <button
            onClick={() => setDetailOpen(true)}
            className="rounded bg-amber-600 px-3 py-1 text-xs font-medium text-white transition hover:bg-amber-700"
          >
            show all metrics
          </button>
        </div>
      </div>

      {/* Main content */}
      <div className="flex-1 overflow-hidden">
        {view === 'topology' && context && (
          <MetricsRouteGraph context={context} />
        )}
        {view === 'charts' && (
          <div className="h-full overflow-auto">
            <MetricsCharts />
          </div>
        )}
      </div>

      {/* Detail modal */}
      {detailOpen && (
        <MetricsDetailModal
          metrics={rawMetrics}
          onClose={() => setDetailOpen(false)}
        />
      )}
    </div>
  );
}
