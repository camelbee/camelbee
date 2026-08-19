import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { Toolbar } from './Toolbar';
import { renderWithProviders } from '@/test/renderWithProviders';
import { useDebuggerStore } from '@/store/debuggerStore';
import type { CamelBeeContext } from '@/types';

const context = {
  name: 'svc',
  framework: 'Spring Boot',
  camelVersion: '4.20.0',
} as unknown as CamelBeeContext;

describe('Toolbar', () => {
  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().setTracing(false);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve('"OK"') }),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders context info and trace/clear controls', () => {
    renderWithProviders(<Toolbar context={context} />);
    expect(screen.getByText('svc')).toBeInTheDocument();
    expect(screen.getByText('Camel 4.20.0')).toBeInTheDocument();
    expect(screen.getByText('Start Tracing')).toBeInTheDocument();
    expect(screen.getByText('Clear')).toBeInTheDocument();
  });

  it('debounces the filter input into the store', async () => {
    renderWithProviders(<Toolbar context={context} />);
    fireEvent.change(screen.getByLabelText('Filter messages'), { target: { value: 'abc' } });
    await waitFor(() => expect(useDebuggerStore.getState().filterText).toBe('abc'));
  });

  it('starts tracing through the delete → activate mutation chain', async () => {
    renderWithProviders(<Toolbar context={context} />);
    fireEvent.click(screen.getByText('Start Tracing'));
    await waitFor(() => expect(useDebuggerStore.getState().isTracing).toBe(true));
    expect(screen.getByText('Stop Tracing')).toBeInTheDocument();
  });

  it('clears messages via the Clear button', async () => {
    const fetchMock = vi.mocked(fetch);
    renderWithProviders(<Toolbar context={context} />);
    fireEvent.click(screen.getByText('Clear'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith('/camelbee/messages', expect.objectContaining({ method: 'DELETE' })),
    );
  });

  // Roadmap #12 (message-cap warning).
  it('shows a warning when the server-side message cap has been reached', () => {
    useDebuggerStore.setState({ capReached: true });
    renderWithProviders(<Toolbar context={context} />);
    expect(screen.getByText('⚠ Message cap reached')).toBeInTheDocument();
  });

  it('hides the cap warning when the cap has not been reached', () => {
    renderWithProviders(<Toolbar context={context} />);
    expect(screen.queryByText('⚠ Message cap reached')).not.toBeInTheDocument();
  });
});

// The "N dynamic hops" toolbar badge was removed 2026-08-15 - it treated "an EIP that's
// inherently impossible to know statically" (e.g. dynamicRouter) the same as "the tracer got
// something wrong", so it fired on every route using such an EIP and taught users to ignore it.
// The dynamically-created node/edge/message flow itself is unaffected - see RouteGraph.test.tsx
// and DebuggerPage's dynamicEdges tracking, which MessagePanel still relies on.

describe('Toolbar capture filter', () => {
  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().setTracing(false);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, statusText: 'OK', text: () => Promise.resolve('"OK"') }),
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const filterCalls = () =>
    vi.mocked(fetch).mock.calls.filter(([url]) => url === '/camelbee/tracer/filter').length;

  it('exposes the capture filter and the display filter as separate controls', () => {
    // the distinction matters: one decides what the server RECORDS, the other only hides rows that
    // were already recorded and already served
    renderWithProviders(<Toolbar context={context} />);

    expect(screen.getByLabelText('Only trace messages containing')).toBeInTheDocument();
    expect(screen.getByLabelText('Filter messages')).toBeInTheDocument();
  });

  it('does not send the capture filter on every keystroke', async () => {
    renderWithProviders(<Toolbar context={context} />);

    fireEvent.change(screen.getByLabelText('Only trace messages containing'), {
      target: { value: 'order-42' },
    });

    // changing it mid-session discards what matched the previous value, so it is applied on Enter
    // or when tracing starts - never per character, unlike the display filter
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(filterCalls()).toBe(0);
  });

  it('applies the capture filter on Enter', async () => {
    renderWithProviders(<Toolbar context={context} />);

    const input = screen.getByLabelText('Only trace messages containing');
    fireEvent.change(input, { target: { value: 'order-42' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(filterCalls()).toBe(1));
  });

  it('applies the capture filter when tracing starts, before anything can be recorded', async () => {
    renderWithProviders(<Toolbar context={context} />);

    fireEvent.change(screen.getByLabelText('Only trace messages containing'), {
      target: { value: 'order-42' },
    });
    fireEvent.click(screen.getByText('Start Tracing'));

    await waitFor(() => expect(filterCalls()).toBe(1));
  });
});
