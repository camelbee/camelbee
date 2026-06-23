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
});
