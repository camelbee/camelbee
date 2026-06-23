import { describe, it, expect, beforeEach } from 'vitest';
import { useDebuggerStore } from './debuggerStore';
import { makeMessage } from '@/test/factories';

const get = () => useDebuggerStore.getState();

describe('debuggerStore', () => {
  beforeEach(() => {
    get().clearMessages();
    get().setFilterText('');
  });

  it('appends messages and keeps only SENDING/SENT in the filtered timeline', () => {
    get().appendMessages(
      [
        makeMessage({ exchangeEventType: 'CREATED' }),
        makeMessage({ exchangeEventType: 'SENDING' }),
        makeMessage({ exchangeEventType: 'SENT' }),
        makeMessage({ exchangeEventType: 'COMPLETED' }),
      ],
      1,
      0,
    );
    const s = get();
    expect(s.messages).toHaveLength(4);
    expect(s.filteredMessages).toHaveLength(2);
    expect(s.timelineIndex).toBe(2);
    expect(s.addVersion).toBe(1);
  });

  it('merges across multiple append calls', () => {
    get().appendMessages([makeMessage({ exchangeEventType: 'SENDING' })], 1, 0);
    get().appendMessages([makeMessage({ exchangeEventType: 'SENT' })], 2, 0);
    expect(get().messages).toHaveLength(2);
    expect(get().filteredMessages).toHaveLength(2);
  });

  it('replaces messages when the reset version changes', () => {
    get().appendMessages([makeMessage({ exchangeEventType: 'SENDING' })], 1, 0);
    get().appendMessages([makeMessage({ exchangeEventType: 'SENT' })], 2, 5); // reset bumped
    expect(get().messages).toHaveLength(1);
    expect(get().resetVersion).toBe(5);
  });

  it('filters by body and headers text', () => {
    get().appendMessages(
      [
        makeMessage({ exchangeEventType: 'SENDING', messageBody: 'hello world', headers: '' }),
        makeMessage({ exchangeEventType: 'SENT', messageBody: 'nope', headers: 'X-Trace: abc' }),
      ],
      1,
      0,
    );
    get().setFilterText('hello');
    expect(get().filteredMessages).toHaveLength(1);
    get().setFilterText('x-trace');
    expect(get().filteredMessages).toHaveLength(1);
  });

  it('steps the timeline forward and back within bounds', () => {
    get().appendMessages(
      [
        makeMessage({ exchangeEventType: 'SENDING' }),
        makeMessage({ exchangeEventType: 'SENT' }),
      ],
      1,
      0,
    );
    get().setTimelineIndex(0);
    get().stepBack();
    expect(get().timelineIndex).toBe(0); // clamped at 0
    get().stepForward();
    expect(get().timelineIndex).toBe(1);
    get().setTimelineIndex(2);
    get().stepForward();
    expect(get().timelineIndex).toBe(2); // clamped at length
  });

  it('tracks tracing flag and selected edge', () => {
    get().setTracing(true);
    expect(get().isTracing).toBe(true);
    get().selectEdge('edge-7');
    expect(get().selectedEdgeId).toBe('edge-7');
    get().clearMessages();
    expect(get().selectedEdgeId).toBeNull();
    expect(get().messages).toHaveLength(0);
  });
});
