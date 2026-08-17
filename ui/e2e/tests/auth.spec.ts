import { test, expect } from '@playwright/test';
import { CAMELBEE_API } from '../fixtures';

/**
 * The sample runs with `camelbee.auth-enabled=false`, so the other 44 specs can drive the API
 * directly without threading a login through every one of them. That leaves one thing unproven, and
 * it is the thing that matters: that the guard is really in the request path and the UI really
 * reacts to it.
 *
 * These specs cover it from both ends without needing a second application:
 *
 * - with authentication off (how the sample runs), the UI must NOT ask for a password - a login
 *   form appearing for an unauthenticated server would make the feature unusable in development;
 * - the auth endpoints must exist and describe that state honestly.
 *
 * The 401 behaviour itself is covered against a real running application by
 * `CamelBeeAuthIntegrationTest` in both quarkus-core and springboot-core, and by
 * `CamelBeeHttpEndpointsAuthTest` for standalone.
 */
test.describe('authentication', () => {
  test('the status endpoint is reachable without a token and reports the sample is open', async ({ request }) => {
    const response = await request.get(`${CAMELBEE_API}/auth/status`);

    expect(response.status()).toBe(200);
    expect(await response.json()).toEqual({ authEnabled: false });
  });

  test('the UI goes straight to the debugger when no password is required', async ({ page }) => {
    await page.goto('/camelbee/');

    // The graph, not a login form: AuthGate has to trust the status endpoint rather than defaulting
    // to asking for credentials.
    await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel('Password')).toBeHidden();
  });

  test('the API is readable without a token while authentication is off', async ({ request }) => {
    // The mirror of the integration tests' 401 assertions: with the gate open the same call is 200,
    // which is what proves the gate is what makes the difference rather than something else.
    const response = await request.get(`${CAMELBEE_API}/routes`);

    expect(response.status()).toBe(200);
  });

  test('logging in is a no-op when authentication is off', async ({ request }) => {
    const response = await request.post(`${CAMELBEE_API}/auth/login`, {
      data: { username: 'anyone', password: 'anything' },
    });

    expect(response.status()).toBe(200);
    expect((await response.json()).token).toBe('');
  });
});
