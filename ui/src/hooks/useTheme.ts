import { useEffect } from 'react';
import { useSettingsStore } from '@/store/settingsStore';

/**
 * Syncs the theme from the settings store to the <html> element class list.
 * Call this once at the app root.
 */
export function useThemeSync() {
  const theme = useSettingsStore((s) => s.theme);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [theme]);
}

/**
 * Returns true if the current theme is dark.
 */
export function useIsDark(): boolean {
  return useSettingsStore((s) => s.theme) === 'dark';
}