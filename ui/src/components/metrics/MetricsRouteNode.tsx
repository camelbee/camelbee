import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { RouteNodeData } from '@/utils/routeGraph';
import { getComponentColors } from '@/utils/colorMap';

export interface MetricsRouteNodeData extends RouteNodeData {
  exchangesTotal?: number;
  exchangesFailed?: number;
}

type Props = NodeProps & { data: MetricsRouteNodeData };

function MetricsRouteNodeInner({ data }: Props) {
  const colors = getComponentColors(
    data.kind === 'error' ? 'error' : data.componentType,
  );

  const total = data.exchangesTotal ?? 0;
  const failed = data.exchangesFailed ?? 0;
  const hasMetrics = total > 0 || failed > 0;
  const isEndpoint = data.kind === 'consumer' || data.kind === 'producer';

  // Traffic light: green if all OK, red if failures, gray if no data
  let trafficColor = 'bg-gray-400 dark:bg-gray-600';
  if (hasMetrics) {
    trafficColor = failed > 0 ? 'bg-red-500' : 'bg-green-500';
  }

  const bgClass = isEndpoint
    ? `${colors.nodeBg} text-white`
    : 'bg-white dark:bg-gray-800';

  return (
    <>
      <Handle type="target" position={Position.Left} className="!bg-gray-400 dark:!bg-gray-500" />
      <div className="flex flex-col items-center gap-1">
        {/* Traffic light */}
        {data.kind !== 'producer' && (
          <div className="flex gap-1">
            <div className={`h-3.5 w-3.5 rounded-full ${trafficColor} shadow-sm ${hasMetrics && failed === 0 ? 'shadow-green-500/50' : failed > 0 ? 'shadow-red-500/50' : ''}`} />
          </div>
        )}

        {/* Node */}
        <div
          className={`flex items-center gap-2 rounded-lg border-l-4 px-3 py-2 shadow-md ${colors.border} ${bgClass}`}
          style={{ width: 200, minHeight: 50 }}
        >
          <div className="min-w-0 flex-1">
            <span
              className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase leading-tight ${
                isEndpoint
                  ? 'bg-white/20 text-white'
                  : `${colors.bg} ${colors.text}`
              }`}
            >
              {data.componentType}
            </span>
            <p
              className={`mt-0.5 truncate text-xs ${isEndpoint ? 'text-white/90' : 'text-gray-700 dark:text-gray-200'}`}
              title={data.label}
            >
              {data.label}
            </p>
          </div>
        </div>

        {/* Exchange count badge */}
        {hasMetrics && (
          <div className="flex gap-1.5 text-xs font-semibold">
            <span className="rounded-full bg-green-500/20 px-2 py-0.5 text-green-600 dark:text-green-400">
              {total - failed}
            </span>
            {failed > 0 && (
              <span className="rounded-full bg-red-500/20 px-2 py-0.5 text-red-600 dark:text-red-400">
                {failed}
              </span>
            )}
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} className="!bg-gray-400 dark:!bg-gray-500" />
    </>
  );
}

export const MetricsRouteNode = memo(MetricsRouteNodeInner);
