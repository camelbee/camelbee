import { test, expect } from '@playwright/test';
import { AUTH_PASSWORD, AUTH_USERNAME } from '../playwright.config';

/**
 * The login, driven the way a user drives it.
 *
 * Every other spec runs against an instance started with `camelbee.auth-enabled=false`, so the guard
 * is only ever proven *absent* there. The 401s themselves are covered by `CamelBeeAuthIntegrationTest`
 * in each core. What neither covers is the part that has to work for the feature to be usable at all:
 * a browser loading the shell, being asked for a password, sending one, and ending up in the debugger
 * with the token attached to every subsequent request.
 *
 * These specs run against a second instance of the same sample, started with the guard on - see
 * `playwright.config.ts`.
 */
test.describe('signing in', () => {
  test('asks for a password instead of showing the debugger', async ({ page }) => {
    await page.goto('/camelbee/');

    await expect(page.getByLabel('Password')).toBeVisible({ timeout: 30_000 });
    // the graph must not be behind the form: a protected server may not render any data at all
    await expect(page.locator('.react-flow__node')).toHaveCount(0);
  });

  test('rejects a wrong password and stays on the form', async ({ page }) => {
    await page.goto('/camelbee/');

    await page.getByLabel('Username').fill(AUTH_USERNAME);
    await page.getByLabel('Password').fill('not-the-password');
    await page.getByRole('button', { name: /sign in/i }).click();

    // the form reports it in its alert region rather than silently doing nothing
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.locator('.react-flow__node')).toHaveCount(0);
  });

  test('signs in with the right password and loads the topology', async ({ page }) => {
    await page.goto('/camelbee/');

    await page.getByLabel('Username').fill(AUTH_USERNAME);
    await page.getByLabel('Password').fill(AUTH_PASSWORD);
    await page.getByRole('button', { name: /sign in/i }).click();

    // the graph appearing proves the token was accepted AND that the UI attached it to the topology
    // request it makes immediately afterwards
    await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel('Password')).toBeHidden();
  });

  test('keeps the session across a reload, and tracing works with a token', async ({ page }) => {
    await page.goto('/camelbee/');
    await page.getByLabel('Username').fill(AUTH_USERNAME);
    await page.getByLabel('Password').fill(AUTH_PASSWORD);
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });

    // sessionStorage survives a reload of the same tab, so the form must not come back
    await page.reload();
    await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByLabel('Password')).toBeHidden();

    // and a write endpoint works too, not only the topology read that follows login
    await page.getByRole('button', { name: 'Start Tracing' }).click();
    await expect(page.getByRole('button', { name: 'Stop Tracing' })).toBeVisible();
    await page.getByRole('button', { name: 'Stop Tracing' }).click();
  });

  test('the api refuses the same calls without a token', async ({ request }) => {
    // the other half of the proof: the UI got in because it holds a token, not because the server
    // is open. Asserted against this instance so both halves are about the same application.
    expect((await request.get('/camelbee/routes')).status()).toBe(401);
    expect((await request.get('/camelbee/messages?index=0')).status()).toBe(401);

    // the shell and the login endpoints stay reachable, or the form could never be shown
    expect((await request.get('/camelbee/index.html')).status()).toBe(200);
    expect((await request.get('/camelbee/auth/status')).status()).toBe(200);
  });

  test('a token from a login is accepted on the api', async ({ request }) => {
    const login = await request.post('/camelbee/auth/login', {
      data: { username: AUTH_USERNAME, password: AUTH_PASSWORD },
    });
    expect(login.status()).toBe(200);
    const token = (await login.json()).token;
    expect(token).not.toBe('');

    const routes = await request.get('/camelbee/routes', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(routes.status()).toBe(200);
    expect((await routes.json()).routes.length).toBeGreaterThan(0);

    // and the rolling refresh comes back, which is what keeps an active session from expiring
    expect(routes.headers()['x-camelbee-token']).toBeTruthy();
  });

  test('a wrong password is refused by the api as well', async ({ request }) => {
    const login = await request.post('/camelbee/auth/login', {
      data: { username: AUTH_USERNAME, password: 'not-the-password' },
    });

    expect(login.status()).toBe(401);
  });
});
