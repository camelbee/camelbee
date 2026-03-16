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
import type { CamelBeeContext, Message } from '@/types';
import {
  buildRouteGraph,
  makeNodeId,
  makeProducerId,
  makeEdgeId,
  sanitize,
  truncateLabel,
  type RouteNode as RNode,
  type MessageEdge as MEdge,
  type ActiveFlow,
} from '@/utils/routeGraph';
import { extractInputUri, extractComponentType } from '@/utils/endpointParser';
import { matchMessageToEdge } from '@/utils/messageMatching';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useIsDark } from '@/hooks/useTheme';
import { RouteNode } from './RouteNode';
import { MessageEdge } from './MessageEdge';

const nodeTypes: NodeTypes = { routeNode: RouteNode as never };
const edgeTypes: EdgeTypes = { messageEdge: MessageEdge as never };

interface RouteGraphProps {
  context: CamelBeeContext;
  onDynamicEdgeAdded?: (edge: MEdge) => void;
}

export function RouteGraph({ context, onDynamicEdgeAdded }: RouteGraphProps) {
  const graph = useMemo(() => buildRouteGraph(context), [context]);
  const isDark = useIsDark();

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

  const updateEdgeData = useCallback(
    (updater: (edges: MEdge[]) => MEdge[]) => {
      setEdges((current) => updater(current as MEdge[]) as never);
    },
    [setEdges],
  );

  // Flow animation counter
  const flowIdRef = useRef(0);
  // Track last processed timeline index to avoid replaying animations on unrelated re-renders
  const lastProcessedTimelineRef = useRef(0);

  /**
   * Create a dynamic edge + node for a message that doesn't match any existing
   * edge. This handles runtime-resolved patterns like dynamicRouter.
   */
  /** Strip double slashes for comparison (tracer uses direct:// but routes use direct:) */
  const stripSlashes = (s: string) => s.replace(/\/\//g, '');

  const createDynamicEdge = useCallback(
    (msg: Message): MEdge | null => {
      if (!msg.routeId || !msg.endpoint || !context) return null;

      const msgRouteId = stripSlashes(msg.routeId);
      const msgEndpoint = stripSlashes(msg.endpoint);

      // Find the source route node by routeId or input URI
      let sourceNodeId: string | null = null;
      let sourceRouteId: string | null = null;
      for (const route of context.routes) {
        const inputUri = extractInputUri(route.input);
        if (route.id === msgRouteId || stripSlashes(inputUri) === msgRouteId) {
          sourceNodeId = makeNodeId(route.id);
          sourceRouteId = route.id;
          break;
        }
      }
      if (!sourceNodeId || !sourceRouteId) return null;

      // Find or create target node
      let targetNodeId: string | null = null;
      let targetRouteId: string | undefined;
      let targetInputUri: string | undefined;
      let targetUri: string | undefined;

      // Check if target is an existing route (direct/seda)
      for (const route of context.routes) {
        const inputUri = extractInputUri(route.input);
        if (stripSlashes(inputUri) === msgEndpoint || route.id === msgEndpoint) {
          targetNodeId = makeNodeId(route.id);
          targetRouteId = route.id;
          targetInputUri = inputUri;
          // Ensure the node exists in the rendered graph
          const existingNode = nodes.find((n) => n.id === targetNodeId);
          if (!existingNode) {
            const componentType = extractComponentType(inputUri);
            const newNode: RNode = {
              id: targetNodeId,
              type: 'routeNode',
              position: { x: 0, y: 0 },
              data: {
                label: truncateLabel(route.id),
                componentType,
                kind: 'internal',
                routeId: route.id,
              },
            };
            const sourceNode = nodes.find((n) => n.id === sourceNodeId);
            if (sourceNode) {
              newNode.position = {
                x: sourceNode.position.x + 300,
                y: sourceNode.position.y + (Math.random() * 200 - 100),
              };
            }
            setNodes((curr) => [...curr, newNode as never]);
          }
          break;
        }
      }

      // If not an existing route, create/find a producer node (external endpoint)
      if (!targetNodeId) {
        const producerUri = msg.endpoint;
        targetNodeId = makeProducerId(producerUri);
        targetUri = producerUri;
        // Ensure the producer node exists
        const existingNode = nodes.find((n) => n.id === targetNodeId);
        if (!existingNode) {
          const componentType = extractComponentType(producerUri);
          const newNode: RNode = {
            id: targetNodeId,
            type: 'routeNode',
            position: { x: 0, y: 0 },
            data: {
              label: truncateLabel(producerUri),
              componentType,
              kind: 'producer',
            },
          };
          const sourceNode = nodes.find((n) => n.id === sourceNodeId);
          if (sourceNode) {
            newNode.position = {
              x: sourceNode.position.x + 300,
              y: sourceNode.position.y + (Math.random() * 200 - 100),
            };
          }
          setNodes((curr) => [...curr, newNode as never]);
        }
      }

      const syntheticOutput = {
        id: `dynamic-${sanitize(msg.routeId)}-${sanitize(msg.endpoint)}`,
        description: `Dynamic[${msg.endpoint}]`,
        delimiter: null,
        type: 'dynamicRouter',
        outputs: [],
      };

      const edgeId = makeEdgeId(sourceNodeId, targetNodeId, syntheticOutput.id);

      // Check if edge already exists
      if (graphEdgesRef.current.some((e) => e.id === edgeId)) {
        return graphEdgesRef.current.find((e) => e.id === edgeId) ?? null;
      }

      const sourceRoute = context.routes.find((r) => r.id === sourceRouteId);
      const sourceInputUriVal = sourceRoute ? extractInputUri(sourceRoute.input) : undefined;

      const newEdge: MEdge = {
        id: edgeId,
        source: sourceNodeId,
        target: targetNodeId,
        type: 'messageEdge',
        data: {
          outputId: syntheticOutput.id,
          sourceRouteId,
          sourceInputUri: sourceInputUriVal,
          targetRouteId,
          targetInputUri,
          targetUri: targetUri ?? msg.endpoint,
          messageCount: 0,
          hasError: false,
          animated: false,
          isErrorHandler: false,
          activeFlows: [],
        },
      };

      // Add to both the ref and the rendered edges
      graphEdgesRef.current = [...graphEdgesRef.current, newEdge];
      setEdges((curr) => [...curr, newEdge as never]);

      // Notify parent so MessagePanel can also see this edge
      onDynamicEdgeAdded?.(newEdge);

      return newEdge;
    },
    [context, nodes, setNodes, setEdges, onDynamicEdgeAdded],
  );

  useEffect(() => {
    const sliced = filteredMessages.slice(0, timelineIndex);

    // Build counts from the full slice
    const counts = new Map<string, { exchanges: Set<string>; hasError: boolean }>();
    for (const msg of sliced) {
      let matched = matchMessageToEdge(msg, graphEdgesRef.current);
      // If no static edge matched, try to create a dynamic one
      if (!matched) {
        matched = createDynamicEdge(msg);
      }
      if (matched) {
        const entry = counts.get(matched.id) ?? { exchanges: new Set(), hasError: false };
        entry.exchanges.add(msg.exchangeId);
        if (msg.messageType === 'ERROR_RESPONSE') entry.hasError = true;
        counts.set(matched.id, entry);
      }
    }

    // Detect new messages for flow animation (only when timeline actually changed)
    const newFlows = new Map<string, ActiveFlow[]>();
    const lastProcessed = lastProcessedTimelineRef.current;
    lastProcessedTimelineRef.current = timelineIndex;
    if (timelineIndex > lastProcessed) {
      const newMessages = filteredMessages.slice(lastProcessed, timelineIndex);
      for (const msg of newMessages) {
        let matched = matchMessageToEdge(msg, graphEdgesRef.current);
        if (!matched) {
          matched = createDynamicEdge(msg);
        }
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
  }, [timelineIndex, filteredMessages, updateEdgeData, createDynamicEdge]);

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
        colorMode={isDark ? 'dark' : 'light'}
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