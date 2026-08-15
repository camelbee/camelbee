import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { RouteNodeData } from '@/utils/routeGraph';
import { getComponentColors } from '@/utils/colorMap';

type Props = NodeProps & { data: RouteNodeData };

/**
 * Build a multi-line hover tooltip with id, description, input URI, error
 * handler and REST info (roadmap #1+15 — Camel's NodeLabelMode.BOTH
 * semantics: description as the label, everything else on hover).
 */
function buildTooltip(data: RouteNodeData): string {
  const lines: string[] = [];
  if (data.routeId) lines.push(`Route: ${data.routeId}`);
  if (data.description) lines.push(`Description: ${data.description}`);
  if (data.inputUri) lines.push(`Input: ${data.inputUri}`);
  // Producer (external endpoint) nodes have no route/description - their label is truncated,
  // so the full URI is the one thing worth showing on hover.
  if (data.fullUri) lines.push(`Endpoint: ${data.fullUri}`);
  if (data.errorHandler) lines.push(`Error handler: ${data.errorHandler}`);
  if (data.isRest) lines.push('REST endpoint');
  return lines.length > 0 ? lines.join('\n') : data.label;
}

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

  // Consumer/producer nodes get a solid technology-colored background; internal nodes stay
  // neutral (white/dark). Selection is shown as a ring layered on top of that background,
  // never by replacing it - swapping to a fixed light background broke text contrast on
  // endpoint nodes, whose white text is only readable against their own dark/colored bg.
  const bgClass = isEndpoint ? `${colors.nodeBg} text-white` : 'bg-white dark:bg-gray-800';
  const selectionRing = selected ? 'ring-2 ring-blue-500 dark:ring-blue-400' : '';

  return (
    <>
      <Handle type="target" position={Position.Left} className="!bg-gray-400 dark:!bg-gray-500" />
      <div
        className={`flex items-center gap-2 rounded-lg border-l-4 px-3 py-2 shadow-md ${colors.border} ${bgClass} ${selectionRing}`}
        style={{ width: 220, minHeight: 60 }}
        title={buildTooltip(data)}
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
