import { memo, useEffect, useState, useCallback, useRef } from 'react';
import {
  getBezierPath,
  BaseEdge,
  type EdgeProps,
} from '@xyflow/react';
import type { MessageEdgeData, ActiveFlow } from '@/utils/routeGraph';
import { useIsDark } from '@/hooks/useTheme';

type Props = EdgeProps & { data: MessageEdgeData | undefined };

/** Parse a cubic bezier SVG path "M sx,sy C cx1,cy1 cx2,cy2 tx,ty" */
function parseCubicBezier(d: string): {
  sx: number; sy: number;
  cx1: number; cy1: number;
  cx2: number; cy2: number;
  tx: number; ty: number;
} | null {
  // getBezierPath produces: "M sx,sy C cx1,cy1 cx2,cy2 tx,ty"
  const nums = d.match(/-?[\d.]+/g);
  if (!nums || nums.length < 8) return null;
  const v = nums.map(Number) as [number, number, number, number, number, number, number, number, ...number[]];
  return { sx: v[0], sy: v[1], cx1: v[2], cy1: v[3], cx2: v[4], cy2: v[5], tx: v[6], ty: v[7] };
}

/** Evaluate cubic bezier at parameter t (0..1) */
function bezierPoint(
  t: number,
  p: ReturnType<typeof parseCubicBezier> & {},
): { x: number; y: number } {
  const u = 1 - t;
  const uu = u * u;
  const uuu = uu * u;
  const tt = t * t;
  const ttt = tt * t;
  return {
    x: uuu * p.sx + 3 * uu * t * p.cx1 + 3 * u * tt * p.cx2 + ttt * p.tx,
    y: uuu * p.sy + 3 * uu * t * p.cy1 + 3 * u * tt * p.cy2 + ttt * p.ty,
  };
}

/** Animated dot that travels along the edge bezier curve */
function FlowDot({
  flow,
  pathData,
  onDone,
  textStroke,
}: {
  flow: ActiveFlow;
  pathData: string;
  onDone: (id: number) => void;
  textStroke: string;
}) {
  const isResponse = flow.type === 'RESPONSE' || flow.type === 'ERROR_RESPONSE';
  const isError = flow.type === 'ERROR_RESPONSE';
  const color = isError ? '#ef4444' : isResponse ? '#f59e0b' : '#22c55e';
  const duration = 700; // ms

  const dotRef = useRef<SVGGElement>(null);

  useEffect(() => {
    const bezier = parseCubicBezier(pathData);
    if (!bezier) return;

    const startTime = performance.now();

    let rafId: number;
    const animate = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / duration, 1);
      // Ease in-out for smoother motion
      const eased = progress < 0.5
        ? 2 * progress * progress
        : 1 - Math.pow(-2 * progress + 2, 2) / 2;
      // Reverse direction for responses
      const t = isResponse ? 1 - eased : eased;
      const point = bezierPoint(t, bezier);

      if (dotRef.current) {
        dotRef.current.setAttribute('transform', `translate(${point.x}, ${point.y})`);
      }

      if (progress < 1) {
        rafId = requestAnimationFrame(animate);
      } else {
        onDone(flow.id);
      }
    };

    rafId = requestAnimationFrame(animate);
    return () => cancelAnimationFrame(rafId);
  }, [pathData, isResponse, duration, flow.id, onDone]);

  return (
    <g ref={dotRef} opacity={0.95}>
      {/* Glow */}
      <circle r={14} fill={color} opacity={0.2} />
      {/* Main dot */}
      <circle r={7} fill={color} />
      {/* Inner bright core */}
      <circle r={3} fill="white" opacity={0.6} />
      {/* Label */}
      <text
        textAnchor="middle"
        y={-16}
        className="text-[9px] font-bold"
        fill={color}
        stroke={textStroke}
        strokeWidth={2}
        paintOrder="stroke"
      >
        {flow.label}
      </text>
    </g>
  );
}

function MessageEdgeInner(props: Props) {
  const {
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
    selected,
  } = props;

  const isDark = useIsDark();
  const defaultStroke = isDark ? '#6b7280' : '#9ca3af'; // gray-500 / gray-400
  const textStroke = isDark ? '#030712' : '#f9fafb'; // gray-950 / gray-50

  const isSelected = selected ?? false;

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  const isErrorHandler = data?.isErrorHandler ?? false;
  const hasMessages = (data?.messageCount ?? 0) > 0;
  const hasError = data?.hasError ?? false;

  let strokeColor = defaultStroke;
  if (isErrorHandler) strokeColor = '#ef4444'; // red-500
  else if (hasError) strokeColor = '#ef4444';
  else if (hasMessages) strokeColor = '#22c55e'; // green-500
  if (isSelected) strokeColor = '#3b82f6'; // blue-500

  const strokeDasharray = isErrorHandler || hasMessages ? '8 4' : undefined;
  const animated = data?.animated ?? false;

  // Track active flow animations
  const [visibleFlows, setVisibleFlows] = useState<ActiveFlow[]>([]);

  useEffect(() => {
    if (data?.activeFlows && data.activeFlows.length > 0) {
      setVisibleFlows((prev) => [...prev, ...data.activeFlows]);
    }
  }, [data?.activeFlows]);

  const handleFlowDone = useCallback((flowId: number) => {
    setVisibleFlows((prev) => prev.filter((f) => f.id !== flowId));
  }, []);

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={{
          stroke: strokeColor,
          strokeWidth: isSelected ? 3 : 2,
          strokeDasharray,
          animation: animated ? 'dash-flow 0.6s linear infinite' : undefined,
        }}
        interactionWidth={20}
      />

      {/* Clickable invisible wider path for easier interaction */}
      <path
        d={edgePath}
        fill="none"
        strokeWidth={20}
        stroke="transparent"
        style={{ cursor: 'pointer' }}
      />

      {/* Message count badge */}
      {hasMessages && (
        <g
          transform={`translate(${labelX}, ${labelY})`}
          style={{ cursor: 'pointer' }}
        >
          <circle r={12} fill={hasError ? '#ef4444' : '#22c55e'} />
          <text
            textAnchor="middle"
            dominantBaseline="central"
            className="text-[10px] font-bold"
            fill="white"
          >
            {data!.messageCount}
          </text>
        </g>
      )}

      {/* Animated flow dots */}
      {visibleFlows.map((flow) => (
        <FlowDot
          key={flow.id}
          flow={flow}
          pathData={edgePath}
          onDone={handleFlowDone}
          textStroke={textStroke}
        />
      ))}
    </>
  );
}

export const MessageEdge = memo(MessageEdgeInner);