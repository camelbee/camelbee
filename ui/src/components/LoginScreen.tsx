import { useState, type FormEvent } from 'react';
import { login } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';

/**
 * The login form, shown in place of the application when the server requires a token.
 *
 * Rendered instead of the app rather than over it: with no token every data call would 401, so a
 * modal on top of a live page would sit above a screen of failed panels.
 */
export function LoginScreen() {
  const setToken = useAuthStore((s) => s.setToken);
  const error = useAuthStore((s) => s.error);
  const setError = useAuthStore((s) => s.setError);

  const [username, setUsername] = useState('camelbee');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const { token } = await login(username, password);
      setToken(token);
    } catch {
      // Deliberately not distinguishing a wrong user from a wrong password: the server does not
      // either, and repeating that here is what keeps it un-probeable.
      setError('Invalid username or password.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-200">
      <form
        onSubmit={onSubmit}
        aria-label="Sign in to CamelBee"
        className="w-80 rounded-lg bg-white p-6 shadow-md dark:bg-gray-900"
      >
        <h1 className="mb-1 text-lg font-semibold text-gray-800 dark:text-gray-100">CamelBee</h1>
        <p className="mb-4 text-xs text-gray-500 dark:text-gray-400">
          Sign in to view the topology and traced messages.
        </p>

        <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300" htmlFor="camelbee-username">
          Username
        </label>
        <input
          id="camelbee-username"
          className="mb-3 w-full rounded border border-gray-300 bg-gray-50 px-2 py-1 text-sm text-gray-800 placeholder-gray-400 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:placeholder-gray-500"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
        />

        <label className="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300" htmlFor="camelbee-password">
          Password
        </label>
        <input
          id="camelbee-password"
          type="password"
          className="mb-4 w-full rounded border border-gray-300 bg-gray-50 px-2 py-1 text-sm text-gray-800 placeholder-gray-400 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-800 dark:text-gray-200 dark:placeholder-gray-500"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        {error && (
          <p role="alert" className="mb-3 text-xs text-red-600 dark:text-red-400">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded bg-amber-500 px-3 py-1.5 text-sm font-medium text-white hover:bg-amber-600 disabled:opacity-50"
        >
          {busy ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="mt-4 text-[11px] leading-snug text-gray-400 dark:text-gray-500">
          No password configured? CamelBee generates one at startup and writes it to the application
          log.
        </p>
      </form>
    </div>
  );
}
