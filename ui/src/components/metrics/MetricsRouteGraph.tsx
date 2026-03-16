import { useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { CamelBeeContext } from '@/types';
import { buildRouteGraph } from '@/utils/routeGraph';
import { useMetricsStore, type RouteMetrics } from '@/store/metricsStore';
import { useIsDark } from '@/hooks/useTheme';
import { MetricsRouteNode, type MetricsRouteNodeData } from './MetricsRouteNode';

const nodeTypes: NodeTypes = { routeNode: MetricsRouteNode as never };

interface MetricsRouteGraphProps {
  context: CamelBeeContext;
}

function applyRouteMetrics(
  nodes: ReturnType<typeof buildRouteGraph>['nodes'],
  routeMetrics: RouteMetrics[],
) {
  const metricsMap = new Map(routeMetrics.map((m) => [m.routeId, m]));
  return nodes.map((node) => {
    const routeId = node.data.routeId;
    const rm = routeId ? metricsMap.get(routeId) : undefined;
    return {
      ...node,
      data: {
        ...node.data,
        exchangesTotal: rm?.exchangesTotal ?? 0,
        exchangesFailed: rm?.exchangesFailed ?? 0,
      } as MetricsRouteNodeData,
    };
  });
}

export function MetricsRouteGraph({ context }: MetricsRouteGraphProps) {
  const graph = useMemo(() => buildRouteGraph(context), [context]);
  const routeMetrics = useMetricsStore((s) => s.routeMetrics);
  const isDark = useIsDark();

  const nodesWithMetrics = useMemo(
    () => applyRouteMetrics(graph.nodes, routeMetrics),
    [graph.nodes, routeMetrics],
  );

  const [nodes, setNodes, onNodesChange] = useNodesState(nodesWithMetrics);
  const [edges, , onEdgesChange] = useEdgesState(graph.edges);

  useEffect(() => {
    setNodes(applyRouteMetrics(graph.nodes, routeMetrics));
  }, [routeMetrics, graph.nodes, setNodes]);

  return (
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        fitView
        minZoom={0.1}
        maxZoom={2}
        proOptions={{ hideAttribution: true }}
        colorMode={isDark ? 'dark' : 'light'}
        nodesDraggable={false}
        nodesConnectable={false}
        edgesFocusable={false}
      >
        <Background color={isDark ? '#374151' : '#d1d5db'} gap={20} />
        <Controls showInteractive={false} />
        <MiniMap
          nodeColor={isDark ? '#4b5563' : '#d1d5db'}
          maskColor={isDark ? 'rgba(0,0,0,0.6)' : 'rgba(255,255,255,0.6)'}
          className={isDark ? '!bg-gray-900' : '!bg-gray-100'}
        />
      </ReactFlow>
    </div>
  );
}