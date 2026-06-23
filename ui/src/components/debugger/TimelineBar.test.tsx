import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { TimelineBar } from './TimelineBar';
import { useDebuggerStore } from '@/store/debuggerStore';
import { makeMessage } from '@/test/factories';

function seed(count: number) {
  useDebuggerStore.getState().clearMessages();
  useDebuggerStore.getState().appendMessages(
    Array.from({ length: count }, () => makeMessage({ exchangeEventType: 'SENDING' })),
    1,
    0,
  );
}

describe('TimelineBar', () => {
  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
  });

  it('shows the current index over the total', () => {
    seed(3);
    useDebuggerStore.getState().setTimelineIndex(2);
    render(<TimelineBar />);
    expect(screen.getByText('2 / 3')).toBeInTheDocument();
  });

  it('disables step-back at the start and step-forward at the end', () => {
    seed(2);
    useDebuggerStore.getState().setTimelineIndex(0);
    render(<TimelineBar />);
    expect(screen.getByLabelText('Step back')).toBeDisabled();
    expect(screen.getByLabelText('Step forward')).toBeEnabled();
  });

  it('advances the timeline when step-forward is clicked', () => {
    seed(2);
    useDebuggerStore.getState().setTimelineIndex(0);
    render(<TimelineBar />);
    fireEvent.click(screen.getByLabelText('Step forward'));
    expect(useDebuggerStore.getState().timelineIndex).toBe(1);
  });

  it('updates the index when the range slider changes', () => {
    seed(4);
    render(<TimelineBar />);
    fireEvent.change(screen.getByLabelText('Timeline position'), { target: { value: '3' } });
    expect(useDebuggerStore.getState().timelineIndex).toBe(3);
  });
});
