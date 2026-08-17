import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { LoginScreen } from './LoginScreen';
import { AuthGate } from './AuthGate';
import { useAuthStore } from '@/store/authStore';
import { renderWithProviders } from '@/test/renderWithProviders';

function stubFetch(handler: (url: string, init?: RequestInit) => unknown) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => handler(url, init)));
}

const ok = (body: unknown) => ({
  ok: true,
  status: 200,
  statusText: 'OK',
  headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : null) },
  text: () => Promise.resolve(JSON.stringify(body)),
});

const unauthorized = () => ({
  ok: false,
  status: 401,
  statusText: 'Unauthorized',
  headers: { get: (name: string) => (name.toLowerCase() === 'content-type' ? 'application/json' : null) },
  text: () => Promise.resolve('{"error":"invalid credentials"}'),
});

describe('LoginScreen', () => {
  beforeEach(() => {
    sessionStorage.clear();
    useAuthStore.setState({ authEnabled: true, token: null, error: null });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('stores the token returned by a successful login', async () => {
    stubFetch(() => ok({ token: 'a-real-token' }));
    renderWithProviders(<LoginScreen />);

    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 's3cret' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(useAuthStore.getState().token).toBe('a-real-token'));
    // Persisted so a reload inside the same tab does not ask again.
    expect(sessionStorage.getItem('camelbee.token')).toBe('a-real-token');
  });

  it('shows an error and stores nothing when the credentials are rejected', async () => {
    stubFetch(() => unauthorized());
    renderWithProviders(<LoginScreen />);

    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(useAuthStore.getState().token).toBeNull();
    expect(sessionStorage.getItem('camelbee.token')).toBeNull();
  });

  it('does not say which of the two was wrong', async () => {
    stubFetch(() => unauthorized());
    renderWithProviders(<LoginScreen />);

    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    // The server refuses to distinguish them; repeating that here is what keeps it un-probeable.
    expect(screen.getByRole('alert').textContent).toBe('Invalid username or password.');
  });
});

describe('AuthGate', () => {
  beforeEach(() => {
    sessionStorage.clear();
    useAuthStore.setState({ authEnabled: null, token: null, error: null });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the application when the server does not require a token', async () => {
    stubFetch(() => ok({ authEnabled: false }));
    renderWithProviders(<AuthGate><p>protected content</p></AuthGate>);

    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });

  it('renders the login form instead of the application when a token is required', async () => {
    stubFetch(() => ok({ authEnabled: true }));
    renderWithProviders(<AuthGate><p>protected content</p></AuthGate>);

    await waitFor(() => expect(screen.getByLabelText('Password')).toBeInTheDocument());
    expect(screen.queryByText('protected content')).not.toBeInTheDocument();
  });

  it('renders neither until the status call resolves', () => {
    stubFetch(() => new Promise(() => {}));
    renderWithProviders(<AuthGate><p>protected content</p></AuthGate>);

    // Otherwise the login form would flash on every load of an unauthenticated application.
    expect(screen.queryByText('protected content')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
  });

  it('falls back to no authentication if the status endpoint is missing', async () => {
    // An older core, or context-enabled=false. Locking the user out of a server that never asked
    // for a password would be the wrong failure mode.
    stubFetch(() => {
      throw new Error('404');
    });
    renderWithProviders(<AuthGate><p>protected content</p></AuthGate>);

    await waitFor(() => expect(screen.getByText('protected content')).toBeInTheDocument());
  });
});
