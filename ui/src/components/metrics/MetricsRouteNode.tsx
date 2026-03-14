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

  // Traffic light: green if all OK, red if failures, gray if no data
  let trafficColor = 'bg-gray-600';
  if (hasMetrics) {
    trafficColor = failed > 0 ? 'bg-red-500' : 'bg-green-500';
  }

  return (
    <>
      <Handle type="target" position={Position.Left} className="!bg-gray-500" />
      <div className="flex flex-col items-center gap-1">
        {/* Traffic light */}
        {data.kind !== 'producer' && (
          <div className="flex gap-1">
            <div className={`h-3 w-3 rounded-full ${trafficColor} shadow-sm ${hasMetrics && failed === 0 ? 'shadow-green-500/50' : failed > 0 ? 'shadow-red-500/50' : ''}`} />
          </div>
        )}

        {/* Node */}
        <div
          className={`flex items-center gap-2 rounded-lg border-l-4 px-3 py-2 shadow-md ${colors.border} bg-gray-800`}
          style={{ width: 200, minHeight: 50 }}
        >
          <div className="min-w-0 flex-1">
            <span
              className={`inline-block rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase leading-tight ${colors.bg} ${colors.text}`}
            >
              {data.componentType}
            </span>
            <p className="mt-0.5 truncate text-xs text-gray-200" title={data.label}>
              {data.label}
            </p>
          </div>
        </div>

        {/* Exchange count badge */}
        {hasMetrics && (
          <div className="flex gap-1 text-[9px]">
            <span className="rounded bg-green-500/20 px-1.5 py-0.5 text-green-400">
              {total - failed}
            </span>
            {failed > 0 && (
              <span className="rounded bg-red-500/20 px-1.5 py-0.5 text-red-400">
                {failed}
              </span>
            )}
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} className="!bg-gray-500" />
    </>
  );
}

export const MetricsRouteNode = memo(MetricsRouteNodeInner);
