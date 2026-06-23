import { describe, it, expect, vi, afterEach } from 'vitest';
import { apiFetch, apiFetchText } from './client';

function mockFetch(body: string, ok = true, status = 200, statusText = 'OK') {
  const fn = vi.fn().mockResolvedValue({
    ok,
    status,
    statusText,
    text: () => Promise.resolve(body),
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('apiFetch', () => {
  it('parses a JSON response', async () => {
    mockFetch(JSON.stringify({ a: 1 }));
    await expect(apiFetch<{ a: number }>('/x')).resolves.toEqual({ a: 1 });
  });

  it('returns undefined for an empty body', async () => {
    mockFetch('');
    await expect(apiFetch('/x')).resolves.toBeUndefined();
  });

  it('returns raw text when the body is not JSON', async () => {
    mockFetch('plain-text');
    await expect(apiFetch<string>('/x')).resolves.toBe('plain-text');
  });

  it('throws on a non-ok response', async () => {
    mockFetch('', false, 500, 'Server Error');
    await expect(apiFetch('/x')).rejects.toThrow('500 Server Error');
  });

  it('sends JSON content-type and merges init', async () => {
    const fn = mockFetch('{}');
    await apiFetch('/x', { method: 'POST', body: '{}' });
    expect(fn).toHaveBeenCalledWith(
      '/x',
      expect.objectContaining({ method: 'POST', headers: { 'Content-Type': 'application/json' } }),
    );
  });
});

describe('apiFetchText', () => {
  it('returns the raw text on success', async () => {
    mockFetch('metric 1');
    await expect(apiFetchText('/m')).resolves.toBe('metric 1');
  });

  it('throws on a non-ok response', async () => {
    mockFetch('', false, 404, 'Not Found');
    await expect(apiFetchText('/m')).rejects.toThrow('404 Not Found');
  });
});
