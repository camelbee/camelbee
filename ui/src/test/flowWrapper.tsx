import { ReactFlowProvider } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import type { ReactNode } from 'react';

/** Wrapper for components that use @xyflow/react hooks/handles. */
export function FlowWrapper({ children }: { children: ReactNode }) {
  return <ReactFlowProvider>{children}</ReactFlowProvider>;
}

/** Build a minimal NodeProps object for unit-rendering a custom node. */
export function nodeProps<T extends Record<string, unknown>>(
  data: T,
  overrides: Partial<NodeProps> = {},
): NodeProps & { data: T } {
  return {
    id: 'n1',
    data,
    selected: false,
    type: 'routeNode',
    dragging: false,
    isConnectable: true,
    positionAbsoluteX: 0,
    positionAbsoluteY: 0,
    zIndex: 0,
    draggable: true,
    selectable: true,
    deletable: true,
    width: 220,
    height: 80,
    ...overrides,
  } as unknown as NodeProps & { data: T };
}
