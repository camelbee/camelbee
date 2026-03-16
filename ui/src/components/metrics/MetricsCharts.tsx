import { useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { useMetricsStore, type TimeSeriesPoint } from '@/store/metricsStore';
import { useIsDark } from '@/hooks/useTheme';

function formatTime(ts: number): string {
  const d = new Date(ts);
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}:${d.getSeconds().toString().padStart(2, '0')}`;
}

interface ChartPanelProps {
  title: string;
  children: React.ReactNode;
}

function ChartPanel({ title, children }: ChartPanelProps) {
  return (
    <div className="rounded border border-gray-300 bg-white/80 dark:border-gray-700 dark:bg-gray-900/80">
      <div className="border-b border-gray-300 px-3 py-1.5 dark:border-gray-700">
        <h3 className="text-xs font-semibold uppercase text-gray-700 dark:text-gray-300">{title}</h3>
      </div>
      <div className="p-2" style={{ height: 200 }}>
        {children}
      </div>
    </div>
  );
}

interface SimpleChartProps {
  series: { data: TimeSeriesPoint[]; name: string; color: string }[];
  unit?: string;
}

function SimpleChart({ series, unit }: SimpleChartProps) {
  const isDark = useIsDark();

  const chartData = useMemo(() => {
    if (series.length === 0 || series[0]!.data.length === 0) return [];
    // Merge all series by timestamp index
    const primary = series[0]!.data;
    return primary.map((p, i) => {
      const point: Record<string, number> = { timestamp: p.timestamp };
      for (const s of series) {
        point[s.name] = s.data[i]?.value ?? 0;
      }
      return point;
    });
  }, [series]);

  if (chartData.length === 0) {
    return (
      <div className="flex h-full items-center justify-center text-xs text-gray-400 dark:text-gray-500">
        Waiting for data...
      </div>
    );
  }

  const gridStroke = isDark ? '#374151' : '#e5e7eb';
  const tickFill = isDark ? '#9ca3af' : '#6b7280';
  const axisStroke = isDark ? '#4b5563' : '#d1d5db';
  const tooltipBg = isDark ? '#1f2937' : '#ffffff';
  const tooltipBorder = isDark ? '1px solid #374151' : '1px solid #d1d5db';

  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke={gridStroke} />
        <XAxis
          dataKey="timestamp"
          tickFormatter={formatTime}
          tick={{ fontSize: 10, fill: tickFill }}
          stroke={axisStroke}
        />
        <YAxis
          tick={{ fontSize: 10, fill: tickFill }}
          stroke={axisStroke}
          width={45}
          tickFormatter={(v: number) => unit === '%' ? `${v.toFixed(0)}%` : v >= 1000 ? `${(v / 1000).toFixed(1)}k` : v.toFixed(0)}
        />
        <Tooltip
          contentStyle={{ backgroundColor: tooltipBg, border: tooltipBorder, fontSize: 11 }}
          labelFormatter={formatTime}
          formatter={(value: number) =>
            unit === '%' ? `${value.toFixed(1)}%` : unit === 'MB' ? `${value.toFixed(1)} MB` : unit === 'ms' ? `${value.toFixed(1)} ms` : value.toFixed(1)
          }
        />
        <Legend wrapperStyle={{ fontSize: 10 }} />
        {series.map((s) => (
          <Line
            key={s.name}
            type="monotone"
            dataKey={s.name}
            stroke={s.color}
            dot={false}
            strokeWidth={1.5}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}

export function MetricsCharts() {
  const cpuUsage = useMetricsStore((s) => s.cpuUsage);
  const processCpu = useMetricsStore((s) => s.processCpu);
  const memoryUsed = useMetricsStore((s) => s.memoryUsed);
  const memoryMax = useMetricsStore((s) => s.memoryMax);
  const gcPauseSum = useMetricsStore((s) => s.gcPauseSum);
  const threadsLive = useMetricsStore((s) => s.threadsLive);
  const threadsDaemon = useMetricsStore((s) => s.threadsDaemon);
  const threadsPeak = useMetricsStore((s) => s.threadsPeak);

  return (
    <div className="grid grid-cols-2 gap-3 p-3">
      <ChartPanel title="CPU Usage">
        <SimpleChart
          series={[
            { data: cpuUsage, name: 'System CPU', color: '#3b82f6' },
            { data: processCpu, name: 'Process CPU', color: '#22c55e' },
          ]}
          unit="%"
        />
      </ChartPanel>

      <ChartPanel title="GC Average Pauses">
        <SimpleChart
          series={[
            { data: gcPauseSum, name: 'GC Pause Total', color: '#f59e0b' },
          ]}
          unit="ms"
        />
      </ChartPanel>

      <ChartPanel title="JVM Memory Usage">
        <SimpleChart
          series={[
            { data: memoryUsed, name: 'Heap Used', color: '#8b5cf6' },
            { data: memoryMax, name: 'Heap Max', color: '#6b7280' },
          ]}
          unit="MB"
        />
      </ChartPanel>

      <ChartPanel title="Threads">
        <SimpleChart
          series={[
            { data: threadsLive, name: 'Live', color: '#06b6d4' },
            { data: threadsDaemon, name: 'Daemon', color: '#f97316' },
            { data: threadsPeak, name: 'Peak', color: '#ef4444' },
          ]}
        />
      </ChartPanel>
    </div>
  );
}