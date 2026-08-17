import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import App from './App';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installApiMock } from '@/test/mockApi';

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the nav bar and the settings route', async () => {
    // Async now: AuthGate asks the server whether a login is required before rendering anything,
    // so nothing is on screen until that resolves.
    installApiMock();
    renderWithProviders(<App />, { route: '/settings' });
    await waitFor(() => expect(screen.getByText('CAMEL BEE')).toBeInTheDocument());
    expect(screen.getByText('theme')).toBeInTheDocument();
  });

  it('redirects unknown routes to the debugger page', async () => {
    installApiMock();
    renderWithProviders(<App />, { route: '/does-not-exist' });
    await waitFor(() => expect(screen.getByText('Start Tracing')).toBeInTheDocument());
  });
});
