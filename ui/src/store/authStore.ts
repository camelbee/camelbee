import { create } from 'zustand';

/**
 * Session state for the CamelBee UI.
 *
 * The token lives in `sessionStorage` rather than `localStorage` deliberately: a debugging session
 * should end when the tab does, and it keeps the credential out of every other tab on the origin.
 * It is held here (not in a cookie) so that no request carries it implicitly - which is also what
 * removes CSRF from the picture, since a cross-origin page cannot set an Authorization header.
 */
const TOKEN_KEY = 'camelbee.token';

export interface AuthState {
  /** Whether the server requires a token at all. `null` until the first status check. */
  authEnabled: boolean | null;
  token: string | null;
  /** Set after a rejected login, cleared on the next attempt. */
  error: string | null;

  setAuthEnabled: (enabled: boolean) => void;
  setToken: (token: string | null) => void;
  setError: (error: string | null) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  authEnabled: null,
  token: sessionStorage.getItem(TOKEN_KEY),
  error: null,

  setAuthEnabled: (authEnabled) => set({ authEnabled }),
  setToken: (token) => {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
    set({ token });
  },
  setError: (error) => set({ error }),
  logout: () => {
    sessionStorage.removeItem(TOKEN_KEY);
    set({ token: null, error: null });
  },
}));

/**
 * Reads the token outside React.
 *
 * `apiFetch` runs from react-query callbacks and cannot use a hook, and reading the store's
 * non-reactive snapshot there is correct: the request needs whatever token is current at the moment
 * it is sent, not the one that was current when a component rendered.
 */
export function currentToken(): string | null {
  return useAuthStore.getState().token;
}

/** Stores a token refreshed by the server, keeping the idle window moving. */
export function applyRefreshedToken(token: string): void {
  useAuthStore.getState().setToken(token);
}

/** Drops the session after the server rejects it, which sends the UI back to the login form. */
export function sessionExpired(): void {
  useAuthStore.getState().logout();
}
