import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useThemeSync, useIsDark } from './useTheme';
import { useSettingsStore } from '@/store/settingsStore';

const setTheme = (t: 'light' | 'dark') => act(() => useSettingsStore.getState().setTheme(t));

describe('useTheme', () => {
  beforeEach(() => {
    useSettingsStore.getState().setTheme('light');
    document.documentElement.classList.remove('dark');
  });

  it('useThemeSync toggles the "dark" class on <html> to match the store', () => {
    renderHook(() => useThemeSync());
    expect(document.documentElement.classList.contains('dark')).toBe(false);

    setTheme('dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    setTheme('light');
    expect(document.documentElement.classList.contains('dark')).toBe(false);
  });

  it('useIsDark reflects the current theme', () => {
    const { result } = renderHook(() => useIsDark());
    expect(result.current).toBe(false);
    setTheme('dark');
    expect(result.current).toBe(true);
  });
});
