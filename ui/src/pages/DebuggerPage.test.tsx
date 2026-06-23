import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { DebuggerPage } from './DebuggerPage';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installApiMock } from '@/test/mockApi';
import { useDebuggerStore } from '@/store/debuggerStore';

describe('DebuggerPage', () => {
  beforeEach(() => {
    useDebuggerStore.getState().clearMessages();
    useDebuggerStore.getState().setTracing(false);
    installApiMock();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows a loading state then the debugger toolbar and timeline', async () => {
    renderWithProviders(<DebuggerPage />);
    expect(screen.getByText('Loading routes…')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Start Tracing')).toBeInTheDocument());
    expect(screen.getByText('Clear')).toBeInTheDocument();
    expect(screen.getByLabelText('Timeline position')).toBeInTheDocument();
  });
});
