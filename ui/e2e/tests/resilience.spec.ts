import { test, expect } from '@playwright/test';
import { CAMELBEE_API, openDebugger, startTracing, triggerPipeline } from '../fixtures';

/**
 * What the UI does when the backend does not cooperate.
 *
 * ErrorBoundary and HealthPanel have unit tests, but nothing exercised them against a server that
 * actually answers badly, and the states below are the ones a user hits first: a management port
 * that is not up yet, and the shipped observability URLs, which are deliberately generic and so
 * 404 on every runtime until they are pointed at the right path.
 *
 * Failures are injected with route interception rather than by stopping the sample, so a broken
 * assertion cannot leave the shared server in a state that fails the rest of the suite.
 */
test.describe('degraded backend', () => {
  test('reports a topology fetch that fails instead of rendering an empty graph', async ({ page }) => {
    await page.route('**/camelbee/routes', (route) =>
      route.fulfill({ status: 503, body: 'no' }));

    await page.goto('/camelbee/');

    // react-query is configured with retry: 1, so this is the state after the retry also failed
    await expect(page.getByText(/^Failed to load routes:/)).toBeVisible({ timeout: 30_000 });
  });

  test('keeps the graph up when the message poll starts failing mid-session', async ({ page, request }) => {
    await openDebugger(page);
    await startTracing(page);

    await page.route('**/camelbee/messages?*', (route) =>
      route.fulfill({ status: 500, body: 'boom' }));
    await triggerPipeline(request);

    // the topology query is independent of the message poll, so the drawn routes have to survive it
    await expect(page.getByRole('button', { name: 'Stop Tracing' })).toBeVisible();
    await expect(page.locator('.react-flow__node').first()).toBeVisible();
  });

  test('warns when the tracer has hit its message cap', async ({ page, request }) => {
    await openDebugger(page);
    await startTracing(page);

    // patch the real payload rather than inventing one: everything except the flag stays authentic,
    // and filling the cap for real would mean pushing camelbee.tracer-max-messages-count exchanges
    await page.route('**/camelbee/messages?*', async (route) => {
      const response = await route.fetch();
      const body = await response.json();
      body.info.capReached = true;
      await route.fulfill({ response, json: body });
    });
    await triggerPipeline(request);

    await expect(page.getByRole('status').filter({ hasText: 'Message cap reached' }))
      .toBeVisible({ timeout: 30_000 });
  });

  test('draws the metrics topology even though the shipped metrics url 404s', async ({ page, request }) => {
    await triggerPipeline(request);
    await openDebugger(page);

    // no settings change first: `/metrics` is what the UI ships with, and standalone serves the
    // scrape at /observe/metrics, so this is what a user sees before configuring anything
    await page.getByRole('link', { name: 'METRICS' }).click();

    await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });
    // fetchMetrics swallows the failure into an empty array, so nodes render without counters
    await expect(page.getByTestId('rf__node-route-musicianProcessorRoute')).toBeVisible();
    await expect(page.getByTestId('rf__node-route-musicianProcessorRoute')
      .locator('span.rounded-full')).toHaveCount(0);
  });

  test('shows the health indicator as unknown while the health url 404s', async ({ page }) => {
    await openDebugger(page);
    await page.getByRole('link', { name: 'METRICS' }).click();

    // HealthPanel titles itself 'Health status' only while it has no response to report
    await expect(page.getByTitle('Health status')).toBeVisible({ timeout: 30_000 });
  });

  test('still answers the api directly when the ui is degraded', async ({ request }) => {
    // a sanity check on the interception-based tests above: none of them touched the real server
    const response = await request.get(`${CAMELBEE_API}/routes`);

    expect(response.status()).toBe(200);
    expect((await response.json()).routes.length).toBeGreaterThan(0);
  });
});
