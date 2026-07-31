import { test, expect } from '@playwright/test';
import { openDebugger, startTracing, triggerPipeline, clickEdge, CAMELBEE_API } from '../fixtures';

const FLAKY_EDGE = 'edge-route-invokeFlakyRoute-route-flakyTargetRoute-flakyEndpoint';
const ENRICH_EDGE = 'edge-route-invokeEnrichRoute-producer-mock_enrich-enrichEndpoint';

/**
 * Live message tracing, end to end: a real request goes through the running sample and the assertions
 * are made on what the shipped UI renders.
 *
 * Tracing is always started from the toolbar rather than over the API, because the UI only polls for
 * messages while its own toggle is on - flipping the server-side tracer alone leaves the graph empty.
 *
 * The sample's timer route is silenced for the whole run (see playwright.config.ts), so the only
 * traffic is what a test sends itself. Without that, background exchanges land mid-assertion and the
 * interaction counts below drift.
 */
test.describe('message tracing', () => {
  /** Generous: a trigger has to complete, then be picked up by the UI's 2s poll. */
  const ARRIVAL = { timeout: 20_000 };

  test.beforeEach(async ({ page, request }) => {
    await openDebugger(page);
    await startTracing(page);
    await triggerPipeline(request);
  });

  test('shows the request and response of a traced hop', async ({ page }) => {
    await clickEdge(page, ENRICH_EDGE);

    // the enrich route sets the body before sending, so it appears as both request and response
    await expect(page.getByText('enrichedData')).toHaveCount(2, ARRIVAL);
    await expect(page.getByRole('heading', { name: 'Request' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Response' })).toBeVisible();
  });

  /**
   * The single most valuable spec here. The dead-letter channel redelivers the failing send twice
   * before it succeeds, so this one edge carries three separate interactions for one exchange.
   * Anything that keys interactions by exchange id alone collapses them into one, and the retry
   * history - the reason someone opens this panel at all - is lost.
   */
  test('walks every attempt of a redelivered send', async ({ page }) => {
    await clickEdge(page, FLAKY_EDGE);

    await expect(page.getByText('Messages (3)')).toBeVisible(ARRIVAL);

    // the panel opens on the last interaction: the attempt that finally succeeded
    await expect(page.getByText('3 / 3')).toBeVisible();
    await expect(page.getByText('Success')).toBeVisible();

    // stepping back reaches the failed attempts, each carrying its exception
    await page.getByRole('button', { name: /Prev/ }).click();
    await expect(page.getByText('2 / 3')).toBeVisible();

    await page.getByRole('button', { name: /Prev/ }).click();
    await expect(page.getByText('1 / 3')).toBeVisible();
    await expect(page.getByText('Error')).toBeVisible();
    await expect(page.getByText(/simulated transient failure/)).toBeVisible();

    // and forward again
    await page.getByRole('button', { name: /Next/ }).click();
    await expect(page.getByText('2 / 3')).toBeVisible();
  });

  /**
   * The strongest end-to-end proof of node-id attribution. The pipeline sends to
   * {@code direct:invokeMockA} twice from the same route - once from the multicast, once from the
   * recipientList - so the graph holds two edges between the same pair of nodes. Nothing about the
   * two hops differs except the node that performed them; without a node id on the traced messages
   * the UI cannot tell them apart and both sets of messages pile onto whichever edge it scans first,
   * leaving the other looking as though it never ran.
   */
  test('gives the multicast and recipientList hops their own messages', async ({ page }) => {
    const bothEdges = page.locator(
      '[data-testid^="rf__edge-edge-route-musicianProcessorRoute-route-invokeMockARoute-"]',
    );
    await expect(bothEdges).toHaveCount(2);

    const edgeIds = await bothEdges.evaluateAll((els) =>
      els.map((el) => el.getAttribute('data-id')!),
    );

    for (const edgeId of edgeIds) {
      await clickEdge(page, edgeId);
      // one request produces exactly one hop down each of the two paths
      await expect(page.getByText('Messages (1)')).toBeVisible(ARRIVAL);
      await page.getByRole('button', { name: 'Close message panel' }).click();
    }
  });

  /**
   * A dynamicRouter picks its targets per exchange, so it contributes no static edge and its hops
   * can only be drawn from traced traffic. Two things have to be right for that to be useful.
   *
   * The arrow has to start at the route that owns the router. The tracer reports the caller of a
   * dynamicRouter continuation as the previously called route, so trusting it drew this hop from
   * invokeMockCRoute - making it look as though invokeMockCRoute calls mock:E, which it does not.
   *
   * And the edge still has to carry its messages. It is sourced around the tracer's routeId, so it
   * can no longer be matched by route; it is matched by the node id of the router instead.
   */
  test('draws a dynamicRouter hop from the route that owns it, with its messages', async ({ page }) => {
    const dynamicEdge = page.locator(
      '[data-testid^="rf__edge-edge-route-musicianProcessorRoute-producer-mock___E"]',
    );
    await expect(dynamicEdge).toHaveCount(1, ARRIVAL);

    // and nothing is drawn from the route that merely ran just before it
    await expect(
      page.locator('[data-testid^="rf__edge-edge-route-invokeMockCRoute-producer-mock___E"]'),
    ).toHaveCount(0);

    const edgeId = await dynamicEdge.getAttribute('data-id');
    await clickEdge(page, edgeId!);

    // one request produces exactly one hop to mock:E - not the router's other hops as well
    await expect(page.getByText('Messages (1)')).toBeVisible(ARRIVAL);
    await expect(page.getByText('invokedMockCBody').first()).toBeVisible();
  });

  /**
   * Camel emits no event for a poll, so these two edges could never carry a message: the graph drew
   * them and they stayed permanently blank, indistinguishable from a hop that was broken. They are
   * now reconstructed from the node itself.
   */
  test('shows messages on the poll and pollEnrich edges', async ({ page }) => {
    const pollEdges = page.locator(
      '[data-testid^="rf__edge-"][data-id*="producer-seda_southbound"][data-id*="poll"]',
    );
    await expect(pollEdges).toHaveCount(2, ARRIVAL);

    const edgeIds = await pollEdges.evaluateAll((els) => els.map((el) => el.getAttribute('data-id')!));

    for (const edgeId of edgeIds) {
      await clickEdge(page, edgeId);
      // one request produces exactly one poll down each path
      await expect(page.getByText('Messages (1)')).toBeVisible(ARRIVAL);
      await expect(page.getByRole('heading', { name: 'Request' })).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Response' })).toBeVisible();
      await page.getByRole('button', { name: 'Close message panel' }).click();
    }
  });

  test('closes the message panel', async ({ page }) => {
    await clickEdge(page, ENRICH_EDGE);
    await expect(page.getByText(/^Messages \(/)).toBeVisible(ARRIVAL);

    await page.getByRole('button', { name: 'Close message panel' }).click();
    await expect(page.getByText(/^Messages \(/)).toHaveCount(0);
  });

  test('stops and restarts tracing from the toolbar', async ({ page, request }) => {
    await page.getByRole('button', { name: 'Stop Tracing' }).click();
    await expect(page.getByRole('button', { name: 'Start Tracing' })).toBeVisible();

    // restarting a session clears the previous one server-side
    await page.getByRole('button', { name: 'Start Tracing' }).click();
    await expect(page.getByRole('button', { name: 'Stop Tracing' })).toBeVisible();

    await expect(async () => {
      const payload = await (await request.get(`${CAMELBEE_API}/messages?index=0`)).json();
      expect(payload.info.count).toBe(0);
    }).toPass({ timeout: 10_000 });

    // and new traffic is picked up and rendered without a reload
    await triggerPipeline(request);
    await clickEdge(page, ENRICH_EDGE);
    await expect(page.getByText('enrichedData')).toHaveCount(2, ARRIVAL);
  });

  /**
   * KNOWN GAP, made visible. The sample produces hops the static topology does not predict: the
   * tracer attributes a routingSlip/dynamicRouter continuation to the previous callee route
   * (`direct://invokeMockC -> direct://invokeMockD`) rather than the route owning the EIP node, so
   * no static edge matches and RouteGraph has to synthesize one. The badge reports that disagreement
   * instead of letting it pass unnoticed.
   *
   * If that attribution is ever fixed the badge disappears and this test must be flipped to assert
   * absence - the diff here is the proof the gap actually closed.
   */
  test('reports hops the static topology did not predict', async ({ page }) => {
    await expect(page.getByText(/dynamic hop/)).toBeVisible(ARRIVAL);
  });

  test('clears traced messages from the toolbar', async ({ page, request }) => {
    await page.getByRole('button', { name: 'Clear' }).click();

    await expect(async () => {
      const payload = await (await request.get(`${CAMELBEE_API}/messages?index=0`)).json();
      expect(payload.info.count).toBe(0);
    }).toPass({ timeout: 10_000 });
  });
});
