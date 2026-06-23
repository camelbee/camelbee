import { describe, it, expect, afterEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import App from './App';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installApiMock } from '@/test/mockApi';

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the nav bar and the settings route', () => {
    renderWithProviders(<App />, { route: '/settings' });
    expect(screen.getByText('CAMEL BEE')).toBeInTheDocument();
    expect(screen.getByText('theme')).toBeInTheDocument();
  });

  it('redirects unknown routes to the debugger page', async () => {
    installApiMock();
    renderWithProviders(<App />, { route: '/does-not-exist' });
    await waitFor(() => expect(screen.getByText('Start Tracing')).toBeInTheDocument());
  });
});
