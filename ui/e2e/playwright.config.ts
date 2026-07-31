import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end configuration for the embedded CamelBee UI.
 *
 * The suite runs against the real thing: the standalone sample is started, and the UI under test is
 * the copy bundled inside camelbee-standalone-core (ui/dist is copied into the jar at build time and
 * served by a Vert.x StaticHandler). Nothing is mocked, so a passing run means the shipped UI, the
 * shipped REST API and a real CamelContext agree with each other.
 *
 * IMPORTANT: because the UI is served from the jar and not from ui/dist directly, editing UI source
 * is not enough - the jar has to be rebuilt. `npm run e2e` does that; `npm test` skips it and is
 * only safe when nothing has changed since the last build.
 *
 * This project is deliberately separate from ../package.json so that the Maven build of the ui
 * module (npm install -> vitest -> vite build) never pulls in Playwright. Run it by hand:
 *   cd ui/e2e && npm install && npm run setup   # one-time
 *   npm run e2e                                 # rebuild the jar, then test
 */
const appPort = process.env.E2E_APP_PORT ?? '18080';
const uiPort = process.env.E2E_UI_PORT ?? '18081';

export const APP_URL = `http://localhost:${appPort}`;
export const UI_URL = `http://localhost:${uiPort}`;

/** Long enough that the timer route never fires during a run. */
const SILENT_TIMER_MS = '86400000';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: UI_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },

  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],

  webServer: {
    command: [
      'mvn -q -f ../..',
      '-pl examples/allcomponent-standalone-sample exec:java',
      `-Dcamel.server.port=${appPort}`,
      `-Dcamel.management.port=${uiPort}`,
      // the sample's http producer calls the application back on its own port
      `-Dcamelbee.sample.self-url=${APP_URL}`,
      // silence the traffic generator: background exchanges would otherwise arrive mid-assertion
      `-Dcamelbee.sample.timer-period=${SILENT_TIMER_MS}`,
      `-Dcamelbee.sample.timer-delay=${SILENT_TIMER_MS}`,
    ].join(' '),
    url: `${UI_URL}/camelbee/`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
