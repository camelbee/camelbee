import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MessagePanel } from './MessagePanel';
import type { MessageEdge, MessageEdgeData } from '@/utils/routeGraph';
import { useDebuggerStore } from '@/store/debuggerStore';
import { makeMessage } from '@/test/factories';

function edge(id: string, data: Partial<MessageEdgeData> = {}): MessageEdge {
  return {
    id,
    source: 's',
    target: 't',
    type: 'messageEdge',
    data: {
      outputId: 'out-1',
      sourceRouteId: 'route1',
      messageCount: 0,
      hasError: false,
      animated: false,
      isErrorHandler: false,
      activeFlows: [],
      ...data,
    },
  };
}

const edges = [edge('edge-1')];

function seedMessages() {
  useDebuggerStore.getState().clearMessages();
  useDebuggerStore.getState().appendMessages(
    [
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'REQUEST', exchangeEventType: 'SENDING', messageBody: 'req-body-A' }),
      makeMessage({ exchangeId: 'A', endpointId: 'out-1', messageType: 'RESPONSE', exchangeEventType: 'SENT', messageBody: 'res-body-A' }),
      makeMessage({ exchangeId: 'B', endpointId: 'out-1', messageType: 'REQUEST', exchangeEventType: 'SENDING', messageBody: 'req-body-B' }),
      makeMessage({ exchangeId: 'B', endpointId: 'out-1', messageType: 'ERROR_RESPONSE', exchangeEventType: 'SENT', messageBody: 'res-body-B', exception: 'boom!' }),
    ],
    1,
    0,
  );
}

describe('MessagePanel', () => {
  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().selectEdge(null);
  });

  it('renders nothing when no edge is selected', () => {
    const { container } = render(<MessagePanel edges={edges} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows interactions for the selected edge', () => {
    seedMessages();
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);
    expect(screen.getByText('Messages (2)')).toBeInTheDocument();
    // Latest interaction (B) is shown by default and is an error.
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('boom!')).toBeInTheDocument();
  });

  it('navigates between interactions with Prev/Next', () => {
    seedMessages();
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
    fireEvent.click(screen.getByText('◀ Prev'));
    expect(screen.getByText('1 / 2')).toBeInTheDocument();
    expect(screen.getByText('Success')).toBeInTheDocument();
  });

  it('closes the panel via the close button', () => {
    seedMessages();
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);
    fireEvent.click(screen.getByLabelText('Close message panel'));
    expect(useDebuggerStore.getState().selectedEdgeId).toBeNull();
  });

  it('jumps the timeline to the current interaction', () => {
    seedMessages();
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);
    fireEvent.click(screen.getByText('Go to timeline position'));
    expect(useDebuggerStore.getState().timelineIndex).toBeGreaterThan(0);
  });
});
