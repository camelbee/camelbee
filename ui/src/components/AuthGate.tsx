import { useEffect, type ReactNode } from 'react';
import { fetchAuthStatus } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { LoginScreen } from '@/components/LoginScreen';

/**
 * Decides whether to show the application or the login form.
 *
 * Three states, and they are distinct on purpose. Until the status call returns, neither is shown -
 * rendering the login form first would make it flash on every load of an unauthenticated
 * application, which is the common case in local development.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const authEnabled = useAuthStore((s) => s.authEnabled);
  const token = useAuthStore((s) => s.token);
  const setAuthEnabled = useAuthStore((s) => s.setAuthEnabled);

  useEffect(() => {
    // A failure here means the endpoint is missing - an older core, or context-enabled=false. Fall
    // back to "no authentication" so the UI behaves as it did before this feature existed, rather
    // than locking the user out of a server that never asked for a password.
    fetchAuthStatus()
      .then((status) => setAuthEnabled(status.authEnabled))
      .catch(() => setAuthEnabled(false));
  }, [setAuthEnabled]);

  if (authEnabled === null) {
    return <div className="h-screen bg-gray-100 dark:bg-gray-950" aria-busy="true" />;
  }

  if (authEnabled && !token) {
    return <LoginScreen />;
  }

  return <>{children}</>;
}
