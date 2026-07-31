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
