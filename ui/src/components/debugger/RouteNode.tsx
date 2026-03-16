import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { RouteNodeData } from '@/utils/routeGraph';
import { getComponentColors } from '@/utils/colorMap';

type Props = NodeProps & { data: RouteNodeData };

function RouteNodeInner({ data, selected }: Props) {
  const colors = getComponentColors(
    data.kind === 'error' ? 'error' : data.componentType,
  );

  const isEndpoint = data.kind === 'consumer' || data.kind === 'producer';

  const kindIcon =
    data.kind === 'consumer'
      ? '⇥'
      : data.kind === 'producer'
        ? '⇤'
        : data.kind === 'error'
          ? '⚠'
          : '';

  // Consumer/producer nodes get a solid technology-colored background
  // Internal nodes stay neutral (white/dark)
  let bgClass: string;
  if (selected) {
    bgClass = 'bg-blue-100 ring-1 ring-blue-500 dark:bg-blue-900/40';
  } else if (isEndpoint) {
    bgClass = `${colors.nodeBg} text-white`;
  } else {
    bgClass = 'bg-white dark:bg-gray-800';
  }

  return (
    <>
      <Handle type="target" position={Position.Left} className="!bg-gray-400 dark:!bg-gray-500" />
      <div
        className={`flex items-center gap-2 rounded-lg border-l-4 px-3 py-2 shadow-md ${colors.border} ${bgClass}`}
        style={{ width: 220, minHeight: 60 }}
      >
        {kindIcon && (
          <span className="text-sm leading-none">{kindIcon}</span>
        )}
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
      <Handle type="source" position={Position.Right} className="!bg-gray-400 dark:!bg-gray-500" />
    </>
  );
}

export const RouteNode = memo(RouteNodeInner);
