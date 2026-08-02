import { test, expect, type Page } from '@playwright/test';
import {
  node,
  openDebugger,
  openMetrics,
  triggerPipeline,
  useStandaloneObservabilityUrls,
} from '../fixtures';

/**
 * End-to-end coverage for the metrics page.
 *
 * The unit tests drive MetricsPage, MetricsCharts and MetricsRouteGraph with hand-written
 * PrometheusMetric arrays, so the whole path from a real scrape to a drawn number is untested: the
 * exposition-format parser in api/metrics.ts, the label matching in metricsStore, and the mapping of
 * per-route counters onto the topology the debugger already draws. This covers that path against the
 * micrometer registry the standalone core actually installs.
 *
 * Counters accumulate for as long as the sample runs, and `reuseExistingServer` means that can span
 * several runs, so nothing here asserts an exact count - only that a route the pipeline touched ends
 * up with a positive one.
 */

/** The green badge on a metrics node: succeeded exchanges (total - failed). */
const succeeded = (page: Page, routeId: string) =>
  page.getByTestId(node(`route-${routeId}`)).locator('span.rounded-full').first();

/** The red badge, rendered only once a route has failed at least one exchange. */
const failed = (page: Page, routeId: string) =>
  page.getByTestId(node(`route-${routeId}`)).locator('span.rounded-full').nth(1);

test.describe('metrics', () => {
  test.beforeEach(async ({ page, request }) => {
    // put some traffic through the routes before the page starts scraping
    await triggerPipeline(request);

    await openDebugger(page);
    await useStandaloneObservabilityUrls(page);
    await openMetrics(page);
  });

  test('draws the same topology as the debugger, with per-route exchange counters', async ({ page }) => {
    await expect(page.getByTestId(node('route-musicianProcessorRoute'))).toBeVisible();

    // the default refresh rate is 5s, and the first scrape has to land before any badge exists
    await expect(succeeded(page, 'musicianProcessorRoute')).toHaveText(/^[1-9]\d*$/, {
      timeout: 30_000,
    });
  });

  test('counts failed exchanges separately from succeeded ones', async ({ page }) => {
    // boomRoute throws on every exchange, so its failure counter is the one that is reliably > 0
    await expect(failed(page, 'boomRoute')).toHaveText(/^[1-9]\d*$/, { timeout: 30_000 });
  });

  test('leaves routes the pipeline never reached without counters', async ({ page }) => {
    // fileListenerRoute is a file consumer that nothing in this suite feeds, so it stays at zero and
    // renders no badge at all - which is what distinguishes "idle" from "failing" on the graph
    await expect(page.getByTestId(node('route-fileListenerRoute'))).toBeVisible();
    await expect(page.getByTestId(node('route-fileListenerRoute')).locator('span.rounded-full'))
      .toHaveCount(0);
  });

  test('renders the charts view on a scrape that carries no jvm series', async ({ page }) => {
    await page.getByRole('button', { name: 'Charts' }).click();

    for (const title of ['CPU Usage', 'GC Average Pauses', 'JVM Memory Usage', 'Threads']) {
      await expect(page.getByText(title, { exact: true })).toBeVisible();
    }

    // These four panels stay empty on standalone: CamelBeeMetrics installs a PrometheusMeterRegistry
    // and the Camel route policy factory, but none of micrometer's JVM binders, so the scrape has no
    // system_cpu_usage / jvm_memory_used_bytes / jvm_threads_* / jvm_gc_pause_seconds series for the
    // store to pick up. Quarkus and Spring Boot get those from their own micrometer integrations.
    // Asserting the panels still render is the regression this can usefully guard: every one of them
    // has to survive a series that is permanently empty.
    await expect(page.locator('.recharts-line-curve')).toHaveCount(0);
  });

  test('lists the raw scrape behind "show all metrics"', async ({ page }) => {
    await page.getByRole('button', { name: 'show all metrics' }).click();

    // series names straight off the wire, so seeing them means the exposition format was parsed
    // rather than just fetched: a name, its labels and a value all survived
    await expect(page.getByText(/camel_exchanges_total/).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/camel_route_policy_seconds/).first()).toBeVisible();
  });

  test('reports the context as healthy', async ({ page }) => {
    await page.getByTitle(/^Status:/).click();

    await expect(page.getByText('UP', { exact: true })).toBeVisible({ timeout: 30_000 });
  });
});
