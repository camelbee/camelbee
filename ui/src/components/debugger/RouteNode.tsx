import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { RouteNodeData } from '@/utils/routeGraph';
import { getComponentColors } from '@/utils/colorMap';

type Props = NodeProps & { data: RouteNodeData };

function RouteNodeInner({ data, selected }: Props) {
  const colors = getComponentColors(
    data.kind === 'error' ? 'error' : data.componentType,
  );

  const kindIcon =
    data.kind === 'consumer'
      ? '⇥'
      : data.kind === 'producer'
        ? '⇤'
        : data.kind === 'error'
          ? '⚠'
          : '';

  return (
    <>
      <Handle type="target" position={Position.Left} className="!bg-gray-500" />
      <div
        className={`flex items-center gap-2 rounded-lg border-l-4 px-3 py-2 shadow-md ${colors.border} ${
          selected ? 'bg-blue-900/40 ring-1 ring-blue-500' : 'bg-gray-800'
        }`}
        style={{ width: 220, minHeight: 60 }}
      >
        {kindIcon && (
          <span className="text-sm leading-none">{kindIcon}</span>
        )}
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
      <Handle type="source" position={Position.Right} className="!bg-gray-500" />
    </>
  );
}

export const RouteNode = memo(RouteNodeInner);
