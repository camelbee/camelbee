import { vi } from 'vitest';
import routesFixture from '../../mock/routes.json';

/**
 * Install a fetch mock that answers the CamelBee endpoints by URL so page-level
 * components can load their data. Returns the mock fn for assertions.
 */
export function installApiMock() {
  const fn = vi.fn(async (url: string) => {
    const respond = (body: unknown, contentType = 'application/json') => ({
      ok: true,
      status: 200,
      statusText: 'OK',
      headers: { get: () => contentType },
      text: () => Promise.resolve(typeof body === 'string' ? body : JSON.stringify(body)),
    });

    if (url.startsWith('/camelbee/routes')) return respond(routesFixture);
    if (url.startsWith('/camelbee/messages')) {
      return respond({ messages: [], info: { count: 0, resetVersion: 0, addVersion: 0, lastModified: '0', lastResetTime: '0' } });
    }
    if (url.includes('health')) return respond({ status: 'UP', checks: [] });
    if (url.includes('metrics')) return respond('system_cpu_usage 0.5\njvm_threads_live_threads 10\n', 'text/plain');
    return respond('"OK"');
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}
