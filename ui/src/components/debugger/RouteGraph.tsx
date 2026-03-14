import { useCallback, useEffect, useMemo, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type NodeTypes,
  type EdgeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { CamelBeeContext } from '@/types';
import { buildRouteGraph, type MessageEdge as MEdge, type ActiveFlow } from '@/utils/routeGraph';
import { matchMessageToEdge } from '@/utils/messageMatching';
import { useDebuggerStore } from '@/store/debuggerStore';
import { RouteNode } from './RouteNode';
import { MessageEdge } from './MessageEdge';

const nodeTypes: NodeTypes = { routeNode: RouteNode as never };
const edgeTypes: EdgeTypes = { messageEdge: MessageEdge as never };

interface RouteGraphProps {
  context: CamelBeeContext;
}

export function RouteGraph({ context }: RouteGraphProps) {
  const graph = useMemo(() => buildRouteGraph(context), [context]);

  const [nodes, setNodes, onNodesChange] = useNodesState(graph.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(graph.edges);

  // Keep a ref to the current graph edges for message matching
  const graphEdgesRef = useRef(graph.edges);

  // When context changes, rebuild
  useEffect(() => {
    setNodes(graph.nodes);
    setEdges(graph.edges);
    graphEdgesRef.current = graph.edges;
  }, [graph, setNodes, setEdges]);

  // Edge click → select edge for message panel
  const selectEdge = useDebuggerStore((s) => s.selectEdge);
  const selectedEdgeId = useDebuggerStore((s) => s.selectedEdgeId);

  const onEdgeClick = useCallback(
    (_: React.MouseEvent, edge: MEdge) => {
      selectEdge(selectedEdgeId === edge.id ? null : edge.id);
    },
    [selectEdge, selectedEdgeId],
  );

  // Mark the selected edge
  useEffect(() => {
    setEdges((curr) =>
      curr.map((e) => ({
        ...e,
        selected: e.id === selectedEdgeId,
      })),
    );
  }, [selectedEdgeId, setEdges]);

  // Subscribe to store for timeline changes
  const filteredMessages = useDebuggerStore((s) => s.filteredMessages);
  const timelineIndex = useDebuggerStore((s) => s.timelineIndex);
  const prevTimelineIndex = useDebuggerStore((s) => s.prevTimelineIndex);

  const updateEdgeData = useCallback(
    (updater: (edges: MEdge[]) => MEdge[]) => {
      setEdges((current) => updater(current as MEdge[]) as never);
    },
    [setEdges],
  );

  // Flow animation counter
  const flowIdRef = useRef(0);

  useEffect(() => {
    const sliced = filteredMessages.slice(0, timelineIndex);

    // Build counts from the full slice
    const counts = new Map<string, { exchanges: Set<string>; hasError: boolean }>();
    for (const msg of sliced) {
      const matched = matchMessageToEdge(msg, graphEdgesRef.current);
      if (matched) {
        const entry = counts.get(matched.id) ?? { exchanges: new Set(), hasError: false };
        entry.exchanges.add(msg.exchangeId);
        if (msg.messageType === 'ERROR_RESPONSE') entry.hasError = true;
        counts.set(matched.id, entry);
      }
    }

    // Detect new messages for flow animation (only when stepping forward)
    const newFlows = new Map<string, ActiveFlow[]>();
    if (timelineIndex > prevTimelineIndex) {
      const newMessages = filteredMessages.slice(prevTimelineIndex, timelineIndex);
      for (const msg of newMessages) {
        const matched = matchMessageToEdge(msg, graphEdgesRef.current);
        if (matched) {
          const flows = newFlows.get(matched.id) ?? [];
          flowIdRef.current += 1;
          const isResponse = msg.messageType === 'RESPONSE' || msg.messageType === 'ERROR_RESPONSE';
          flows.push({
            id: flowIdRef.current,
            type: msg.messageType as ActiveFlow['type'],
            label: isResponse ? 'RSP' : 'REQ',
          });
          newFlows.set(matched.id, flows);
        }
      }
    }

    updateEdgeData((currentEdges) =>
      currentEdges.map((e) => {
        const stats = counts.get(e.id);
        const count = stats?.exchanges.size ?? 0;
        return {
          ...e,
          data: {
            ...e.data!,
            messageCount: count,
            hasError: stats?.hasError ?? false,
            animated: count > 0,
            activeFlows: newFlows.get(e.id) ?? [],
          },
        };
      }),
    );
  }, [timelineIndex, prevTimelineIndex, filteredMessages, updateEdgeData]);

  return (
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onEdgeClick={onEdgeClick}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        minZoom={0.1}
        maxZoom={2}
        proOptions={{ hideAttribution: true }}
        colorMode="dark"
      >
        <Background color="#374151" gap={20} />
        <Controls showInteractive={false} />
        <MiniMap
          nodeColor="#4b5563"
          maskColor="rgba(0,0,0,0.6)"
          className="!bg-gray-900"
        />
      </ReactFlow>
    </div>
  );
}
