import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import {
  WaterfallPanel,
  MAX_SPANS_PER_FLOW,
  ALWAYS_SHOW_SLOWEST,
  AUTO_COLLAPSE_ABOVE,
} from './WaterfallPanel';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useSettingsStore } from '@/store/settingsStore';
import { makeMessage } from '@/test/factories';
import type { Message } from '@/types';
import type { MessageEdge, MessageEdgeData } from '@/utils/routeGraph';

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

/** Messages built by `hop` default to routeId 'route1' / endpointId 'out-1', matching `edge()`. */
const linkedEdges = [edge('edge-1')];

function hop(exchangeId: string, endpoint: string, at: number, took: number, over: Partial<Message> = {}) {
  return [
    makeMessage({
      exchangeId,
      endpoint,
      exchangeEventType: 'SENDING',
      messageType: 'REQUEST',
      timeStamp: String(at - took),
      timeTaken: 0,
      ...over,
    }),
    makeMessage({
      exchangeId,
      endpoint,
      exchangeEventType: 'SENT',
      messageType: 'RESPONSE',
      timeStamp: String(at),
      timeTaken: took,
      ...over,
    }),
  ];
}

function seed(messages: Message[]) {
  useDebuggerStore.getState().clearMessages();
  useDebuggerStore.getState().appendMessages(messages, 1, 1, false);
}

beforeEach(() => {
  useDebuggerStore.getState().clearMessages();
  useSettingsStore.setState({ waterfallHeight: 256 });
});

/** Drag the grip by `dy` px; negative is upwards, which makes the panel taller. */
function dragHandle(dy: number) {
  fireEvent.mouseDown(screen.getByTestId('waterfall-resize-handle'), { clientY: 500 });
  fireEvent.mouseMove(window, { clientY: 500 + dy });
  fireEvent.mouseUp(window);
}

describe('WaterfallPanel', () => {
  it('tells the user what to do when nothing has been traced', () => {
    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByText(/No timed hops yet/i)).toBeInTheDocument();
  });

  it('renders a bar per hop with its duration', () => {
    seed([...hop('ex-1', 'http://svc', 1000, 250), ...hop('ex-1', 'mock:after', 1300, 40)]);

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByText('http://svc')).toBeInTheDocument();
    expect(screen.getByText('mock:after')).toBeInTheDocument();
    expect(screen.getByText('250ms')).toBeInTheDocument();
    expect(screen.getByText('40ms')).toBeInTheDocument();
    expect(screen.getAllByTestId('waterfall-bar')).toHaveLength(2);
  });

  it('shows a child exchange inside its parent flow, not as a separate one', () => {
    seed([
      ...hop('root', 'mock:before', 1000, 100),
      ...hop('child', 'mock:tapped', 1200, 50, { parentExchangeId: 'root' }),
    ]);

    render(<WaterfallPanel onClose={() => {}} />);

    // one flow header, both hops under it
    expect(screen.getByText(/2 hops/)).toBeInTheDocument();
    expect(screen.getByText('mock:tapped')).toBeInTheDocument();
  });

  it('indents a child hop deeper than its parent', () => {
    seed([
      ...hop('root', 'mock:before', 1000, 100),
      ...hop('child', 'mock:tapped', 1200, 50, { parentExchangeId: 'root' }),
    ]);

    render(<WaterfallPanel onClose={() => {}} />);

    const parentLabel = screen.getByText('mock:before');
    const childLabel = screen.getByText('mock:tapped');

    expect(parentLabel).toHaveStyle({ paddingLeft: '0px' });
    expect(childLabel).toHaveStyle({ paddingLeft: '10px' });
  });

  it('marks a flow containing a failed hop as an error', () => {
    seed([
      ...hop('ex-1', 'mock:ok', 1000, 10),
      ...hop('ex-1', 'http://bad', 1100, 20, {
        messageType: 'ERROR_RESPONSE',
        exception: 'kaboom',
      }),
    ]);

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.queryByText('OK')).not.toBeInTheDocument();
  });

  it('shows a dash rather than a fabricated duration for a hop with no response', () => {
    seed([
      makeMessage({
        exchangeId: 'ex-1',
        endpoint: 'http://slow',
        exchangeEventType: 'SENDING',
        messageType: 'REQUEST',
        timeStamp: '1000',
        timeTaken: 0,
      }),
    ]);

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('collapses and expands a flow', () => {
    seed(hop('ex-1', 'http://svc', 1000, 250));

    render(<WaterfallPanel onClose={() => {}} />);

    const header = screen.getByRole('button', { expanded: true });
    expect(screen.getByText('http://svc')).toBeInTheDocument();

    fireEvent.click(header);
    expect(screen.queryByText('http://svc')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { expanded: false }));
    expect(screen.getByText('http://svc')).toBeInTheDocument();
  });

  it('honours the timeline scrubber instead of always showing everything', () => {
    seed([...hop('ex-1', 'mock:a', 1000, 10), ...hop('ex-2', 'mock:b', 2000, 10)]);
    // rewind to before any message was recorded
    useDebuggerStore.getState().setTimelineIndex(0);

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByText(/No timed hops yet/i)).toBeInTheDocument();
  });

  it('calls onClose from the close button', () => {
    const onClose = vi.fn();
    render(<WaterfallPanel onClose={onClose} />);

    fireEvent.click(screen.getByLabelText('Close waterfall'));

    expect(onClose).toHaveBeenCalledOnce();
  });

  it('positions a bar by when the hop started, not at the left edge', () => {
    // mock:b runs in the second half of the flow, so its bar must be offset
    seed([...hop('ex-1', 'mock:a', 1000, 100), ...hop('ex-1', 'mock:b', 1400, 200)]);

    render(<WaterfallPanel onClose={() => {}} />);

    const bars = screen.getAllByTestId('waterfall-bar');
    expect(bars[0]).toHaveStyle({ left: '0%' });
    expect(bars[1]!.getAttribute('style')).toMatch(/left: 60%/);
  });

  it('applies the stored height', () => {
    useSettingsStore.setState({ waterfallHeight: 320 });

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByTestId('waterfall-panel')).toHaveStyle({ height: '320px' });
  });

  it('grows when the grip is dragged upwards', () => {
    render(<WaterfallPanel onClose={() => {}} />);

    dragHandle(-100);

    expect(useSettingsStore.getState().waterfallHeight).toBe(356);
    expect(screen.getByTestId('waterfall-panel')).toHaveStyle({ height: '356px' });
  });

  it('shrinks when the grip is dragged downwards', () => {
    render(<WaterfallPanel onClose={() => {}} />);

    dragHandle(60);

    expect(useSettingsStore.getState().waterfallHeight).toBe(196);
  });

  it('stops shrinking at the minimum instead of collapsing to nothing', () => {
    render(<WaterfallPanel onClose={() => {}} />);

    dragHandle(5000);

    expect(useSettingsStore.getState().waterfallHeight).toBe(140);
  });

  it('never grows past the viewport, so the graph keeps room', () => {
    render(<WaterfallPanel onClose={() => {}} />);

    dragHandle(-5000);

    expect(useSettingsStore.getState().waterfallHeight).toBeLessThanOrEqual(
      window.innerHeight - 160,
    );
  });

  it('ignores pointer movement once the drag has ended', () => {
    render(<WaterfallPanel onClose={() => {}} />);

    dragHandle(-100);
    fireEvent.mouseMove(window, { clientY: 10 });

    expect(useSettingsStore.getState().waterfallHeight).toBe(356);
  });

  it('resizes from the keyboard when the grip is focused', () => {
    render(<WaterfallPanel onClose={() => {}} />);
    const handle = screen.getByTestId('waterfall-resize-handle');

    fireEvent.keyDown(handle, { key: 'ArrowUp' });
    expect(useSettingsStore.getState().waterfallHeight).toBe(280);

    fireEvent.keyDown(handle, { key: 'ArrowDown' });
    expect(useSettingsStore.getState().waterfallHeight).toBe(256);
  });

  it('caps the rows one flow may draw, and says how many are hidden', () => {
    // MAX_FLOWS does not bound the DOM on its own: a split() over a large body puts every item's
    // exchange in ONE flow. Unbounded, 10k traced messages of that shape rendered 25k DOM nodes and
    // took ~540ms per render, repeated on every 2s poll.
    const total = MAX_SPANS_PER_FLOW + 20;
    const many: Message[] = [];
    for (let i = 0; i < total; i++) {
      many.push(...hop('ex-1', `mock://h${i}`, 1000 + i * 10, 5));
    }
    seed(many);

    render(<WaterfallPanel onClose={() => {}} />);

    // every hop is 5ms, so none is an outlier worth rescuing and the cap applies cleanly
    expect(screen.getAllByTestId('waterfall-bar')).toHaveLength(MAX_SPANS_PER_FLOW);
    expect(screen.getByText(/\+ 20 more hops not shown/)).toBeInTheDocument();
  });

  it('never hides the slowest hop, however far past the cap it falls', () => {
    // the panel exists to answer "why was this slow" - truncating to the first N rows alone would
    // hide the one hop the user opened it to find
    const total = MAX_SPANS_PER_FLOW * 3;
    const slowIndex = MAX_SPANS_PER_FLOW * 2; // well past the cap
    const many: Message[] = [];
    for (let i = 0; i < total; i++) {
      many.push(...hop('ex-1', `mock://h${i}`, 1000 + i * 10, i === slowIndex ? 900 : 1));
    }
    seed(many);

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getByText(`mock://h${slowIndex}`)).toBeInTheDocument();
    expect(screen.getByText('900ms')).toBeInTheDocument();
    // and it is still bounded
    expect(screen.getAllByTestId('waterfall-bar').length).toBeLessThanOrEqual(
      MAX_SPANS_PER_FLOW + ALWAYS_SHOW_SLOWEST,
    );
  });

  it('still counts and times every hop in the header, including the hidden ones', () => {
    const total = MAX_SPANS_PER_FLOW + 20;
    const many: Message[] = [];
    for (let i = 0; i < total; i++) {
      many.push(...hop('ex-1', `mock://h${i}`, 1000 + i * 10, 5));
    }
    seed(many);

    render(<WaterfallPanel onClose={() => {}} />);

    // the cap is a rendering limit, not a data limit - the summary must not lie
    expect(screen.getByText(new RegExp(`${total} hops`))).toBeInTheDocument();
  });

  it('keeps every flow expanded while there are only a few', () => {
    const messages: Message[] = [];
    for (let i = 0; i < AUTO_COLLAPSE_ABOVE; i++) {
      messages.push(...hop(`ex-${i}`, `mock://e${i}`, 1000 + i * 100, 10));
    }
    seed(messages);

    render(<WaterfallPanel onClose={() => {}} />);

    for (let i = 0; i < AUTO_COLLAPSE_ABOVE; i++) {
      expect(screen.getByText(`mock://e${i}`)).toBeInTheDocument();
    }
  });

  it('collapses all but the newest once there are many flows', () => {
    const count = AUTO_COLLAPSE_ABOVE + 3;
    const messages: Message[] = [];
    for (let i = 0; i < count; i++) {
      messages.push(...hop(`ex-${i}`, `mock://e${i}`, 1000 + i * 100, 10));
    }
    seed(messages);

    render(<WaterfallPanel onClose={() => {}} />);

    // newest first, so the last one is the only one left open
    expect(screen.getByText(`mock://e${count - 1}`)).toBeInTheDocument();
    expect(screen.queryByText('mock://e0')).not.toBeInTheDocument();
    expect(screen.getAllByTestId('waterfall-bar')).toHaveLength(1);
  });

  it('lets a default-collapsed flow be opened, and the newest be closed', () => {
    const count = AUTO_COLLAPSE_ABOVE + 3;
    const messages: Message[] = [];
    for (let i = 0; i < count; i++) {
      messages.push(...hop(`ex-${i}`, `mock://e${i}`, 1000 + i * 100, 10));
    }
    seed(messages);

    render(<WaterfallPanel onClose={() => {}} />);

    const headers = screen.getAllByRole('button', { expanded: false });
    fireEvent.click(headers[0]!);
    expect(screen.getAllByTestId('waterfall-bar')).toHaveLength(2);

    fireEvent.click(
      screen.getByRole('button', { expanded: true, name: new RegExp(`ex-${count - 1}`) }),
    );
    expect(screen.getAllByTestId('waterfall-bar')).toHaveLength(1);
  });

  it('counts flows in the header', () => {
    seed([...hop('ex-1', 'mock:a', 1000, 10), ...hop('ex-2', 'mock:b', 2000, 10)]);

    render(<WaterfallPanel onClose={() => {}} />);

    const panel = screen.getByTestId('waterfall-panel');
    expect(within(panel).getByText(/2 flows/)).toBeInTheDocument();
  });
});

describe('WaterfallPanel <-> topology linking', () => {
  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().selectEdge(null);
    useSettingsStore.setState({ waterfallHeight: 256 });
  });

  it('selects the matching topology edge when a bar is clicked', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));

    render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);
    fireEvent.click(screen.getByTestId('waterfall-row'));

    expect(useDebuggerStore.getState().selectedEdgeId).toBe('edge-1');
  });

  it('clicking the same bar again clears the selection', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));

    render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);
    fireEvent.click(screen.getByTestId('waterfall-row'));
    fireEvent.click(screen.getByTestId('waterfall-row-selected'));

    expect(useDebuggerStore.getState().selectedEdgeId).toBeNull();
  });

  it('highlights the bars of an edge selected from the topology', () => {
    // the reverse direction: RouteGraph sets selectedEdgeId, and the waterfall must react
    seed(hop('ex-1', 'direct://next', 1000, 5));
    useDebuggerStore.getState().selectEdge('edge-1');

    render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    expect(screen.getByTestId('waterfall-row-selected')).toBeInTheDocument();
  });

  it('expands a collapsed flow so the selected edge is actually visible', () => {
    // without this the topology -> waterfall direction would silently do nothing whenever the flow
    // happened to be one of the auto-collapsed ones
    const messages: Message[] = [];
    for (let i = 0; i < AUTO_COLLAPSE_ABOVE + 3; i++) {
      messages.push(...hop(`ex-${i}`, 'direct://next', 1000 + i * 100, 5));
    }
    seed(messages);
    // the OLDEST flow renders last and would be collapsed by default
    useDebuggerStore.getState().selectEdge('edge-1');

    render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    // every flow matches this edge here, so all of them are revealed rather than just the newest
    expect(screen.getAllByTestId('waterfall-row-selected').length).toBeGreaterThan(1);
  });

  it('leaves a bar inert when no topology edge matches it', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));

    render(<WaterfallPanel onClose={() => {}} edges={[edge('edge-1', { outputId: 'somewhere-else' })]} />);
    fireEvent.click(screen.getByTestId('waterfall-row'));

    // no edge matched, so clicking must not select an arbitrary one
    expect(useDebuggerStore.getState().selectedEdgeId).toBeNull();
  });

  it('works from the keyboard', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));

    render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);
    fireEvent.keyDown(screen.getByTestId('waterfall-row'), { key: 'Enter' });

    expect(useDebuggerStore.getState().selectedEdgeId).toBe('edge-1');
  });

  it('renders unlinked when no edges are supplied at all', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));

    render(<WaterfallPanel onClose={() => {}} />);

    expect(screen.getAllByTestId('waterfall-bar')).toHaveLength(1);
    expect(screen.queryByTestId('waterfall-row-selected')).not.toBeInTheDocument();
  });
});

describe('WaterfallPanel scroll-into-view', () => {
  let scrolled: Element[];

  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().selectEdge(null);
    scrolled = [];
    // jsdom has no layout, so scrollIntoView is not implemented - record the calls instead
    Element.prototype.scrollIntoView = function scrollIntoViewStub(this: Element) {
      scrolled.push(this);
    };
  });

  it('scrolls the highlighted row into view when the graph selects an edge', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));
    const { rerender } = render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    expect(scrolled).toHaveLength(0);

    useDebuggerStore.getState().selectEdge('edge-1');
    rerender(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    expect(scrolled).toHaveLength(1);
    expect(scrolled[0]).toHaveAttribute('data-testid', 'waterfall-row-selected');
  });

  it('does NOT re-scroll on later renders while the same edge stays selected', () => {
    // the panel re-renders on every 2s poll; re-scrolling then would fight the user's own scrolling
    seed(hop('ex-1', 'direct://next', 1000, 5));
    useDebuggerStore.getState().selectEdge('edge-1');
    const { rerender } = render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    expect(scrolled).toHaveLength(1);

    rerender(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);
    rerender(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    expect(scrolled).toHaveLength(1);
  });

  it('scrolls again once a different edge is selected', () => {
    seed([
      ...hop('ex-1', 'direct://next', 1000, 5),
      ...hop('ex-1', 'direct://other', 1100, 5, { endpointId: 'out-2' }),
    ]);
    const twoEdges = [edge('edge-1'), edge('edge-2', { outputId: 'out-2' })];
    useDebuggerStore.getState().selectEdge('edge-1');
    const { rerender } = render(<WaterfallPanel onClose={() => {}} edges={twoEdges} />);

    useDebuggerStore.getState().selectEdge('edge-2');
    rerender(<WaterfallPanel onClose={() => {}} edges={twoEdges} />);

    expect(scrolled.length).toBeGreaterThan(1);
  });

  it('does not scroll when the selection is cleared', () => {
    seed(hop('ex-1', 'direct://next', 1000, 5));
    useDebuggerStore.getState().selectEdge('edge-1');
    const { rerender } = render(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);
    const afterSelect = scrolled.length;

    useDebuggerStore.getState().selectEdge(null);
    rerender(<WaterfallPanel onClose={() => {}} edges={linkedEdges} />);

    expect(scrolled).toHaveLength(afterSelect);
  });
});
