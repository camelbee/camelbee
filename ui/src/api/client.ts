import { applyRefreshedToken, currentToken, sessionExpired } from '../store/authStore';

/** Sent back by the server on every authenticated request, extending the idle window. */
const REFRESHED_TOKEN_HEADER = 'X-CamelBee-Token';

/**
 * Adds the bearer token, if the session has one.
 *
 * A header rather than a cookie: the browser never attaches it on its own, so a cross-origin page
 * cannot make an authenticated request on the user's behalf.
 */
function withAuth(init?: RequestInit): RequestInit {
  const token = currentToken();
  return {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  };
}

/**
 * Handles the two things every authenticated response can carry: a refreshed token, and a rejection.
 *
 * The refresh is what makes the timeout an IDLE one - the UI polls every couple of seconds while the
 * debugger is open, so an active user is continuously re-issued a token and an inactive one expires.
 */
function handleAuth(res: Response): void {
  // Optional chaining because a Response is not always a real one: several suites stub fetch with a
  // bare object, and a missing header is indistinguishable from a server that sent none anyway.
  const refreshed = res.headers?.get(REFRESHED_TOKEN_HEADER);
  if (refreshed) applyRefreshedToken(refreshed);
  // 401 on any call means the session is gone - expired, or the server was restarted with a new
  // generated password. Clearing it returns the app to the login form rather than leaving every
  // panel showing a stale error.
  if (res.status === 401) sessionExpired();
}

/** Base fetch wrapper with JSON handling */
export async function apiFetch<T>(
  url: string,
  init?: RequestInit,
): Promise<T> {
  const res = await fetch(url, withAuth(init));
  handleAuth(res);
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
  const text = await res.text();
  if (!text) return undefined as T;
  try {
    return JSON.parse(text) as T;
  } catch {
    return text as T;
  }
}

/** Fetch raw text (used for Prometheus metrics) */
export async function apiFetchText(url: string): Promise<string> {
  // Metrics and health are the host framework's endpoints, not CamelBee's, so they are outside the
  // guard - but the token is sent anyway in case an application has protected them itself.
  const res = await fetch(url, withAuth());
  handleAuth(res);
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText}`);
  }
  return res.text();
}
