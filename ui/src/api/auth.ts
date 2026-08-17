import { apiFetch } from './client';

export interface AuthStatus {
  authEnabled: boolean;
}

export interface LoginResponse {
  token: string;
}

/**
 * Asks whether authentication is switched on.
 *
 * Deliberately unauthenticated - the UI has to know whether to render a login form before it has
 * any credential to present. It reveals only that CamelBee is installed and configured, which the
 * first 401 would tell an unauthenticated caller anyway.
 */
export async function fetchAuthStatus(): Promise<AuthStatus> {
  return apiFetch<AuthStatus>('/camelbee/auth/status');
}

/** Exchanges credentials for a session token. Throws on a rejected login. */
export async function login(username: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/camelbee/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}
