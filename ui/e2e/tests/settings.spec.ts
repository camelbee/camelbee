import { test, expect } from '@playwright/test';
import { openDebugger, openMetrics } from '../fixtures';
import { UI_URL } from '../playwright.config';

/**
 * The Settings page is small, but everything on it is a promise to the rest of the UI: the health
 * and metrics URLs decide what those pages poll, and every value has to survive a reload or the
 * user re-enters it on every visit.
 *
 * The two things worth asserting in a browser rather than in a unit test are exactly those - real
 * localStorage across a real reload, and a changed URL actually reaching the network layer.
 */
test.describe('settings', () => {
  test.beforeEach(async ({ page }) => {
    await openDebugger(page);
    await page.getByRole('link', { name: 'SETTINGS' }).click();
  });

  test('shows the shipped defaults', async ({ page }) => {
    await expect(page.getByLabel('health url')).toHaveValue('/health');
    await expect(page.getByLabel('metrics url')).toHaveValue('/metrics');
    await expect(page.getByLabel('health refresh rate')).toHaveValue('5');
    await expect(page.getByLabel('metrics history')).toHaveValue('300');
    await expect(page.getByLabel('max characters in a text field')).toHaveValue('10000');
  });

  test('every value survives a full page reload', async ({ page }) => {
    await page.getByLabel('health url').fill('/observe/health');
    await page.getByLabel('metrics url').fill('/observe/metrics');
    await page.getByLabel('health refresh rate').fill('7');
    await page.getByLabel('max characters in a text field').fill('2500');

    await page.reload();
    await page.getByRole('link', { name: 'SETTINGS' }).click();

    // persisted through real localStorage, not just component state
    await expect(page.getByLabel('health url')).toHaveValue('/observe/health');
    await expect(page.getByLabel('metrics url')).toHaveValue('/observe/metrics');
    await expect(page.getByLabel('health refresh rate')).toHaveValue('7');
    await expect(page.getByLabel('max characters in a text field')).toHaveValue('2500');
  });

  test('switches theme, and the choice sticks', async ({ page }) => {
    const html = page.locator('html');

    await page.getByRole('button', { name: 'Dark', exact: true }).click();
    await expect(html).toHaveClass(/dark/);

    await page.reload();
    await expect(html).toHaveClass(/dark/);

    await page.getByRole('link', { name: 'SETTINGS' }).click();
    await page.getByRole('button', { name: 'Light', exact: true }).click();
    await expect(html).not.toHaveClass(/dark/);
  });

  test('clamps values outside the supported range instead of accepting them', async ({ page }) => {
    // the store clamps rather than validating, so a nonsense value silently becomes a sane one -
    // asserted here because the polling code downstream trusts these bounds
    await page.getByLabel('health refresh rate').fill('999');
    await expect(page.getByLabel('health refresh rate')).toHaveValue('10');

    await page.getByLabel('metrics history').fill('1');
    await expect(page.getByLabel('metrics history')).toHaveValue('300');

    await page.getByLabel('max characters in a text field').fill('99999');
    await expect(page.getByLabel('max characters in a text field')).toHaveValue('30000');
  });

  test('a changed metrics url is the one actually polled', async ({ page }) => {
    // the point of the page: not that the field holds a string, but that the string is used
    const requested: string[] = [];
    page.on('request', (r) => {
      if (r.url().includes('metrics')) requested.push(r.url());
    });

    await page.getByLabel('metrics url').fill('/observe/metrics');
    await openMetrics(page);

    await expect
      .poll(() => requested.some((u) => u.includes('/observe/metrics')), { timeout: 15_000 })
      .toBe(true);
    expect(requested.some((u) => u === `${UI_URL}/metrics`)).toBe(false);
  });
});
