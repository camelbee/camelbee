import { test, expect } from '@playwright/test';
import { openDebugger, node, edge } from '../fixtures';

/**
 * The topology half of the graph is fully deterministic - it is derived from the route definitions,
 * not from traffic - so it can be asserted exactly.
 *
 * These specs deliberately assert on node and edge *identity*, never on geometry. Dagre coordinates
 * change whenever a node is added and asserting on them produces a suite that breaks for no reason.
 */
test.describe('route topology', () => {
  test.beforeEach(async ({ page }) => {
    await openDebugger(page);
  });

  test('renders a node for every route in the sample', async ({ page }) => {
    const expected = [
      'route-postMusicianRoute', 'route-getMusicianRoute', 'route-healthRoute',
      'route-musicianProcessorRoute', 'route-timerRoute', 'route-fileListenerRoute',
      'route-invokeHttpRoute', 'route-invokeWireTapRoute',
      'route-invokeEnrichRoute', 'route-invokeEnrichDynamicRoute',
      'route-invokeSedaRoute', 'route-invokeFileRoute',
      'route-invokeFlakyRoute', 'route-flakyTargetRoute',
      'route-invokeAlwaysFailsRoute', 'route-boomRoute',
      'route-invokeAlwaysFailsDlqRoute', 'route-boomDlqRoute', 'route-deadLetterRoute',
      'route-invokeMockARoute', 'route-invokeMockBRoute',
      'route-invokeMockCRoute', 'route-invokeMockDRoute',
    ];

    for (const id of expected) {
      await expect(page.getByTestId(node(id))).toBeAttached();
    }
  });

  test('keeps the rest verbs as their own nodes, separate from the routes behind them', async ({ page }) => {
    // Camel inlines rest verbs into their target route by default; the sample turns that off so the
    // REST hop stays visible. If inlining is ever re-enabled these nodes disappear.
    for (const id of ['route-postMusician', 'route-getMusician', 'route-healthCheck']) {
      await expect(page.getByTestId(node(id))).toBeAttached();
    }
    await expect(page.getByTestId(edge('edge-route-postMusician-route-postMusicianRoute-to12')))
      .toBeAttached();
  });

  /**
   * The producer sends to `direct:invokeHttp?block=true` while the consumer is `direct:invokeHttp`.
   * If the query string is not normalized away before matching, this edge does not exist and the
   * whole downstream branch silently detaches from the graph.
   */
  test('draws an edge across a query string on a direct: target', async ({ page }) => {
    await expect(
      page.getByTestId(edge('edge-route-musicianProcessorRoute-route-invokeHttpRoute-httpBridgeEndpoint')),
    ).toBeAttached();
    await expect(
      page.getByTestId(edge('edge-route-invokeFlakyRoute-route-flakyTargetRoute-flakyEndpoint')),
    ).toBeAttached();
  });

  test('resolves a static toD to the route it addresses', async ({ page }) => {
    await expect(
      page.getByTestId(edge('edge-route-musicianProcessorRoute-route-invokeSedaRoute-toD1')),
    ).toBeAttached();
  });

  test('draws one edge per EIP fan-out target', async ({ page }) => {
    const fanOut = [
      // wireTap
      'edge-route-musicianProcessorRoute-route-invokeWireTapRoute-wireTap1',
      // multicast
      'edge-route-musicianProcessorRoute-route-invokeMockARoute-to5',
      'edge-route-musicianProcessorRoute-route-invokeMockBRoute-to6',
      // both enrich forms
      'edge-route-musicianProcessorRoute-route-invokeEnrichRoute-enrich1',
      'edge-route-musicianProcessorRoute-route-invokeEnrichDynamicRoute-enrich2',
      // recipientList splits on its delimiter into three targets
      'edge-route-musicianProcessorRoute-route-invokeMockARoute-recipientList1',
      'edge-route-musicianProcessorRoute-route-invokeMockBRoute-recipientList1',
      'edge-route-musicianProcessorRoute-route-invokeFileRoute-recipientList1',
      // routingSlip into two
      'edge-route-musicianProcessorRoute-route-invokeMockCRoute-routingSlip1',
      'edge-route-musicianProcessorRoute-route-invokeMockDRoute-routingSlip1',
    ];

    for (const id of fanOut) {
      await expect(page.getByTestId(edge(id))).toBeAttached();
    }
  });

  /**
   * `to("seda:southbound")` is drained by poll()/pollEnrich(), never by a `from("seda:southbound")`
   * route. An internal endpoint with no consumer used to be dropped from the graph entirely, taking
   * the messages traced on it with it.
   */
  test('renders an internal endpoint that no route consumes', async ({ page }) => {
    await expect(page.getByTestId(node('producer-seda_southbound'))).toBeAttached();
    await expect(
      page.getByTestId(edge('edge-route-invokeWireTapRoute-producer-seda_southbound-sedaProducerEndpoint')),
    ).toBeAttached();
  });

  test('renders the poll() target as a producer node', async ({ page }) => {
    await expect(page.getByTestId(node('producer-log_polled'))).toBeAttached();
    await expect(page.getByTestId(edge('edge-route-invokeSedaRoute-producer-log_polled-to10')))
      .toBeAttached();
  });

  test('links every route that inherits the dead-letter channel to it', async ({ page }) => {
    for (const routeId of ['musicianProcessorRoute', 'invokeFlakyRoute', 'timerRoute', 'invokeAlwaysFailsDlqRoute']) {
      await expect(
        page.getByTestId(edge(`edge-route-${routeId}-route-deadLetterRoute-errorHandler-${routeId}`)),
      ).toBeAttached();
    }

    // routes that opted out with noErrorHandler() have no such edge
    for (const routeId of ['flakyTargetRoute', 'boomDlqRoute']) {
      await expect(
        page.getByTestId(edge(`edge-route-${routeId}-route-deadLetterRoute-errorHandler-${routeId}`)),
      ).toHaveCount(0);
    }
  });

  test('shows the runtime the UI is attached to', async ({ page }) => {
    await expect(page.getByText('camelbee-standalone-sample')).toBeVisible();
    await expect(page.getByText(/Standalone - Camel/)).toBeVisible();
    await expect(page.getByText(/^Camel \d+\./)).toBeVisible();
  });

  /**
   * A truncated description read as an awkward prose fragment and was harder to scan against the
   * route id used in the Java source than the id itself, so the node label always shows the id -
   * the description (when present) moves to the hover tooltip instead (see RouteNode.tsx).
   */
  test('labels a route with its id even when it has a description, which moves to the tooltip', async ({ page }) => {
    const routeNode = page.getByTestId(node('route-invokeSedaRoute'));
    await expect(routeNode).toContainText('invokeSedaRoute');
    await expect(routeNode).not.toContainText('Drains the internal queue');
    // the title attribute lives on RouteNode's own wrapper div, a child of React Flow's node div
    await expect(routeNode.locator('[title]')).toHaveAttribute('title', /Drains the internal queue/);
  });
});
