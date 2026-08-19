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

/**
 * A second instance of the same sample, started with authentication ON.
 *
 * The main instance runs open so the other specs can drive the API without threading a login through
 * every one of them - which leaves the login itself covered only by unit tests (the form) and
 * integration tests (the 401s). Nothing typed a password into the real form in a real browser. This
 * instance exists for exactly that, and `auth.spec.ts` is the only file that runs against it.
 */
const authAppPort = process.env.E2E_AUTH_APP_PORT ?? '18082';
const authUiPort = process.env.E2E_AUTH_UI_PORT ?? '18083';

export const APP_URL = `http://localhost:${appPort}`;
export const UI_URL = `http://localhost:${uiPort}`;
export const AUTH_UI_URL = `http://localhost:${authUiPort}`;

/** The credentials the protected instance is started with. */
export const AUTH_USERNAME = 'camelbee';
export const AUTH_PASSWORD = 'e2e-secret';

/** Long enough that the timer route never fires during a run. */
const SILENT_TIMER_MS = '86400000';

/** Shared webServer settings; only the command and the port differ between the two instances. */
const server = {
  reuseExistingServer: !process.env.CI,
  timeout: 180_000,
  stdout: 'ignore' as const,
  stderr: 'pipe' as const,
};

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
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
      testIgnore: /auth-required\.spec\.ts/,
    },
    {
      // the protected instance; only the login specs run here
      name: 'chromium-auth',
      use: { ...devices['Desktop Chrome'], baseURL: AUTH_UI_URL },
      testMatch: /auth-required\.spec\.ts/,
    },
  ],

  webServer: [
    {
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
      ...server,
    },
    {
      // the same sample with the guard on, so the login form is exercised against a real server
      command: [
        'mvn -q -f ../..',
        '-pl examples/allcomponent-standalone-sample exec:java',
        `-Dcamel.server.port=${Number(authAppPort)}`,
        `-Dcamel.management.port=${authUiPort}`,
        '-Dcamelbee.auth-enabled=true',
        `-Dcamelbee.username=${AUTH_USERNAME}`,
        `-Dcamelbee.password=${AUTH_PASSWORD}`,
        `-Dcamelbee.sample.self-url=http://localhost:${authAppPort}`,
        `-Dcamelbee.sample.timer-period=${SILENT_TIMER_MS}`,
        `-Dcamelbee.sample.timer-delay=${SILENT_TIMER_MS}`,
      ].join(' '),
      // the shell is public by necessity - a browser has to load the app to show a login form - so
      // it is still a valid readiness probe for a protected instance
      url: `${AUTH_UI_URL}/camelbee/`,
      ...server,
    },
  ],
});
