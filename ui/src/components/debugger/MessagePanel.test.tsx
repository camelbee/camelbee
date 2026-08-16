import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MessagePanel } from './MessagePanel';
import type { MessageEdge, MessageEdgeData } from '@/utils/routeGraph';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useSettingsStore } from '@/store/settingsStore';
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

  // Roadmap #9 (latency): the response's timeTaken is shown next to the status badge.
  it('shows the response duration when timeTaken is present', () => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().appendMessages(
      [
        makeMessage({ exchangeId: 'C', endpointId: 'out-1', messageType: 'REQUEST', exchangeEventType: 'SENDING', messageBody: 'req-body-C' }),
        makeMessage({ exchangeId: 'C', endpointId: 'out-1', messageType: 'RESPONSE', exchangeEventType: 'SENT', messageBody: 'res-body-C', timeTaken: 77 }),
      ],
      1,
      0,
    );
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);
    expect(screen.getByText('77ms')).toBeInTheDocument();
  });

  it('omits the duration when timeTaken is 0 (SENDING/no timing data)', () => {
    seedMessages();
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);
    expect(screen.queryByText(/^\d+ms$/)).not.toBeInTheDocument();
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

  // Roadmap #18 (loop/retry-safe interactions): a send/error/retry/success
  // sequence on the same exchangeId must walk as 3 distinct interactions via
  // Prev/Next, in chronological order, with no skipped or duplicated steps.
  it('walks a retry sequence (send/error/retry/success) one attempt at a time via Prev/Next', () => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().appendMessages(
      [
        makeMessage({ exchangeId: 'R', endpointId: 'out-1', messageType: 'REQUEST', exchangeEventType: 'SENDING', messageBody: 'attempt-1-req' }),
        makeMessage({ exchangeId: 'R', endpointId: 'out-1', messageType: 'ERROR_RESPONSE', exchangeEventType: 'SENT', messageBody: 'attempt-1-res', exception: 'timeout' }),
        makeMessage({ exchangeId: 'R', endpointId: 'out-1', messageType: 'REQUEST', exchangeEventType: 'SENDING', messageBody: 'attempt-2-req' }),
        makeMessage({ exchangeId: 'R', endpointId: 'out-1', messageType: 'ERROR_RESPONSE', exchangeEventType: 'SENT', messageBody: 'attempt-2-res', exception: 'timeout' }),
        makeMessage({ exchangeId: 'R', endpointId: 'out-1', messageType: 'REQUEST', exchangeEventType: 'SENDING', messageBody: 'attempt-3-req' }),
        makeMessage({ exchangeId: 'R', endpointId: 'out-1', messageType: 'RESPONSE', exchangeEventType: 'SENT', messageBody: 'attempt-3-res' }),
      ],
      1,
      0,
    );
    useDebuggerStore.getState().selectEdge('edge-1');
    render(<MessagePanel edges={edges} />);

    // Defaults to the latest attempt (3rd), and reports all 3 attempts.
    expect(screen.getByText('Messages (3)')).toBeInTheDocument();
    expect(screen.getByText('3 / 3')).toBeInTheDocument();
    expect(screen.getByText('Success')).toBeInTheDocument();

    // Step back through attempt 2 then attempt 1 — no skipped/duplicated steps.
    fireEvent.click(screen.getByText('◀ Prev'));
    expect(screen.getByText('2 / 3')).toBeInTheDocument();
    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('timeout')).toBeInTheDocument();

    fireEvent.click(screen.getByText('◀ Prev'));
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
    expect(screen.getByText('Error')).toBeInTheDocument();

    // Prev is disabled at the first attempt.
    expect((screen.getByText('◀ Prev') as HTMLButtonElement).disabled).toBe(true);

    // Walk forward again, ending back at the success.
    fireEvent.click(screen.getByText('Next ▶'));
    expect(screen.getByText('2 / 3')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Next ▶'));
    expect(screen.getByText('3 / 3')).toBeInTheDocument();
    expect(screen.getByText('Success')).toBeInTheDocument();
    expect((screen.getByText('Next ▶') as HTMLButtonElement).disabled).toBe(true);
  });
});

describe('MessagePanel resizing', () => {
  beforeEach(() => {
    useSettingsStore.setState({ messagePanelWidth: 400 });
    seedMessages();
    useDebuggerStore.getState().selectEdge('edge-1');
  });

  /** Drag the grip by `dx` px; negative is leftwards, which makes the panel wider. */
  function dragHandle(dx: number) {
    fireEvent.mouseDown(screen.getByTestId('message-panel-resize-handle'), { clientX: 900 });
    fireEvent.mouseMove(window, { clientX: 900 + dx });
    fireEvent.mouseUp(window);
  }

  it('applies the stored width', () => {
    useSettingsStore.setState({ messagePanelWidth: 520 });

    render(<MessagePanel edges={edges} />);

    expect(screen.getByTestId('message-panel')).toHaveStyle({ width: '520px' });
  });

  it('grows when the grip is dragged left', () => {
    render(<MessagePanel edges={edges} />);

    dragHandle(-150);

    expect(useSettingsStore.getState().messagePanelWidth).toBe(550);
    expect(screen.getByTestId('message-panel')).toHaveStyle({ width: '550px' });
  });

  it('shrinks when the grip is dragged right', () => {
    render(<MessagePanel edges={edges} />);

    dragHandle(80);

    expect(useSettingsStore.getState().messagePanelWidth).toBe(320);
  });

  it('stops shrinking at the minimum instead of collapsing', () => {
    render(<MessagePanel edges={edges} />);

    dragHandle(5000);

    expect(useSettingsStore.getState().messagePanelWidth).toBe(280);
  });

  it('never grows past the viewport, so the graph keeps room', () => {
    render(<MessagePanel edges={edges} />);

    dragHandle(-5000);

    expect(useSettingsStore.getState().messagePanelWidth).toBeLessThanOrEqual(
      window.innerWidth - 240,
    );
  });

  it('resizes from the keyboard, with left growing the panel', () => {
    render(<MessagePanel edges={edges} />);
    const handle = screen.getByTestId('message-panel-resize-handle');

    fireEvent.keyDown(handle, { key: 'ArrowLeft' });
    expect(useSettingsStore.getState().messagePanelWidth).toBe(424);

    fireEvent.keyDown(handle, { key: 'ArrowRight' });
    expect(useSettingsStore.getState().messagePanelWidth).toBe(400);
  });
});
