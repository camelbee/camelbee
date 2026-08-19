import { expect, type Page, type APIRequestContext } from '@playwright/test';
import { APP_URL, UI_URL } from './playwright.config';

export const CAMELBEE_API = `${UI_URL}/camelbee`;

/** React Flow tags every node and edge with a test id derived from the graph id. */
export const node = (id: string) => `rf__node-${id}`;
export const edge = (id: string) => `rf__edge-${id}`;

/**
 * Opens the debugger and waits until the topology has been fetched and laid out.
 */
export async function openDebugger(page: Page) {
  await page.goto('/camelbee/');
  await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });
}

/**
 * Starts a tracing session from the toolbar, which is the only way to get the UI polling: the store's
 * `isTracing` flag gates the message query, so flipping the server-side tracer over the API leaves
 * the graph empty. Clicking it also clears any messages left over from a previous test.
 */
export async function startTracing(page: Page) {
  await page.getByRole('button', { name: 'Start Tracing' }).click();
  await expect(page.getByRole('button', { name: 'Stop Tracing' })).toBeVisible();
}

/** Sends one musician through the whole pipeline. */
export async function triggerPipeline(request: APIRequestContext) {
  const response = await request.post(`${APP_URL}/api/musicians`, {
    headers: { 'Content-Type': 'application/json' },
    data: { name: 'Coltrane', instrument: 'Sax' },
  });
  expect(response.status()).toBe(200);
}

/**
 * Points the UI at the observability endpoints this runtime actually serves.
 *
 * The shipped defaults are `/health` and `/metrics`, which no runtime serves as-is - each user is
 * expected to set them for theirs (Quarkus `/q/metrics`, Spring Boot `/actuator/prometheus`,
 * standalone `/observe/metrics`). Going through the settings form rather than seeding localStorage
 * keeps the store, the form and the queries in the assertion.
 */
export async function useStandaloneObservabilityUrls(page: Page) {
  await page.getByRole('link', { name: 'SETTINGS' }).click();

  // by accessible name rather than by position - the inputs used to be reachable only as
  // textbox().first()/.nth(1), which silently follows whatever order the form happens to be in
  const healthInput = page.getByLabel('health url');
  await expect(healthInput).toHaveValue('/health');
  await healthInput.fill('/observe/health');

  const metricsInput = page.getByLabel('metrics url');
  await expect(metricsInput).toHaveValue('/metrics');
  await metricsInput.fill('/observe/metrics');
}

/** Opens the metrics page and waits for the route topology to be laid out. */
export async function openMetrics(page: Page) {
  await page.getByRole('link', { name: 'METRICS' }).click();
  await expect(page.locator('.react-flow__node').first()).toBeVisible({ timeout: 30_000 });
}

/**
 * Clicks a React Flow edge and waits for the message panel to open.
 *
 * The click has to land on the wide invisible interaction path, not the visible stroke: React Flow's
 * stylesheet sets `pointer-events: none` on `.react-flow__edge-path`, so clicking the first path in
 * the group silently does nothing.
 */
export async function clickEdge(page: Page, edgeId: string) {
  const group = page.getByTestId(edge(edgeId));
  await expect(group).toBeAttached();

  // dispatchEvent rather than click(): an edge is a curved SVG path, so the centre of its bounding
  // box is usually not on the path itself and a positional click lands on whatever is underneath.
  await group.locator('path.react-flow__edge-interaction').dispatchEvent('click');

  await expect(page.getByText(/^Messages \(/)).toBeVisible({ timeout: 10_000 });
}
