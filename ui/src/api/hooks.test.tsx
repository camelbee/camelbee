import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { createQueryWrapper } from '@/test/queryWrapper';
import { useRoutes } from './routes';
import { useMetrics } from './metrics';
import { useMessages, useTraceStatus, useDeleteMessages } from './messages';
import { useHealth } from './health';

function stubFetch(impl: (url: string, init?: RequestInit) => unknown) {
  const fn = vi.fn(async (url: string, init?: RequestInit) => {
    const value = impl(url, init);
    return {
      ok: true,
      status: 200,
      statusText: 'OK',
      text: () => Promise.resolve(typeof value === 'string' ? value : JSON.stringify(value)),
    };
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useRoutes', () => {
  it('fetches the topology from /camelbee/routes', async () => {
    stubFetch(() => ({ routes: [], name: 'svc' }));
    const { wrapper } = createQueryWrapper();
    const { result } = renderHook(() => useRoutes(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toMatchObject({ name: 'svc' });
  });
});

describe('useMetrics', () => {
  it('fetches and parses Prometheus text when enabled', async () => {
    stubFetch(() => 'system_cpu_usage 0.5\n');
    const { wrapper } = createQueryWrapper();
    const { result } = renderHook(() => useMetrics(true, 5, '/q/metrics'), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([{ name: 'system_cpu_usage', value: 0.5 }]);
  });

  it('does not fetch when disabled', () => {
    const fn = stubFetch(() => '');
    const { wrapper } = createQueryWrapper();
    renderHook(() => useMetrics(false), { wrapper });
    expect(fn).not.toHaveBeenCalled();
  });
});

describe('useMessages', () => {
  it('passes index/version params in the query string', async () => {
    const fn = stubFetch(() => ({ messages: [], info: {} }));
    const { wrapper } = createQueryWrapper();
    const { result } = renderHook(() => useMessages(3, 7, 1, true), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(fn.mock.calls[0]![0]).toContain('index=3');
    expect(fn.mock.calls[0]![0]).toContain('addVersion=7');
    expect(fn.mock.calls[0]![0]).toContain('resetVersion=1');
  });
});

describe('useHealth', () => {
  it('returns null when the request fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 503, statusText: 'Down', text: () => Promise.resolve('') }),
    );
    const { wrapper } = createQueryWrapper();
    const { result } = renderHook(() => useHealth(true, 5), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });
});

describe('mutations', () => {
  it('useTraceStatus POSTs the status to the tracer endpoint', async () => {
    const fn = stubFetch(() => 'OK');
    const { wrapper } = createQueryWrapper();
    const { result } = renderHook(() => useTraceStatus(), { wrapper });
    await result.current.mutateAsync('ACTIVE');
    expect(fn).toHaveBeenCalledWith(
      '/camelbee/tracer/status',
      expect.objectContaining({ method: 'POST', body: JSON.stringify('ACTIVE') }),
    );
  });

  it('useDeleteMessages DELETEs the messages endpoint', async () => {
    const fn = stubFetch(() => 'OK');
    const { wrapper } = createQueryWrapper();
    const { result } = renderHook(() => useDeleteMessages(), { wrapper });
    await result.current.mutateAsync();
    expect(fn).toHaveBeenCalledWith(
      '/camelbee/messages',
      expect.objectContaining({ method: 'DELETE' }),
    );
  });
});
