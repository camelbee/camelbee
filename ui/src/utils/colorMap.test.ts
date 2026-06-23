import { describe, it, expect } from 'vitest';
import { getComponentColors, _safelistColors } from './colorMap';

describe('getComponentColors', () => {
  it('maps a known component type to its color tokens', () => {
    expect(getComponentColors('kafka')).toEqual({
      bg: 'bg-orange-500/20',
      border: 'border-orange-500',
      text: 'text-orange-500',
      nodeBg: 'bg-orange-500',
    });
  });

  it('is case-insensitive', () => {
    expect(getComponentColors('KAFKA')).toEqual(getComponentColors('kafka'));
  });

  it('maps aliases that share a color', () => {
    expect(getComponentColors('seda').border).toBe(getComponentColors('direct').border);
  });

  it('falls back to gray-500 for unknown types', () => {
    expect(getComponentColors('totally-unknown')).toEqual({
      bg: 'bg-gray-500/20',
      border: 'border-gray-500',
      text: 'text-gray-500',
      nodeBg: 'bg-gray-500',
    });
  });

  it('every generated token is present in the safelist', () => {
    const known = ['direct', 'kafka', 'http', 'mongodb', 'error', 'mock'];
    for (const type of known) {
      const c = getComponentColors(type);
      expect(_safelistColors).toContain(c.bg);
      expect(_safelistColors).toContain(c.border);
      expect(_safelistColors).toContain(c.text);
      expect(_safelistColors).toContain(c.nodeBg);
    }
  });
});
