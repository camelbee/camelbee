import { describe, it, expect, beforeEach } from 'vitest';
import { useSettingsStore } from './settingsStore';

const initial = useSettingsStore.getState();

describe('settingsStore', () => {
  beforeEach(() => {
    useSettingsStore.setState(initial, true);
  });

  it('sets theme and URLs verbatim', () => {
    const s = useSettingsStore.getState();
    s.setTheme('dark');
    s.setHealthUrl('/q/health');
    s.setMetricsUrl('/q/metrics');
    const next = useSettingsStore.getState();
    expect(next.theme).toBe('dark');
    expect(next.healthUrl).toBe('/q/health');
    expect(next.metricsUrl).toBe('/q/metrics');
  });

  it('clamps refresh rates to [2..10]', () => {
    const s = useSettingsStore.getState();
    s.setHealthRefreshRate(99);
    expect(useSettingsStore.getState().healthRefreshRate).toBe(10);
    s.setHealthRefreshRate(0);
    expect(useSettingsStore.getState().healthRefreshRate).toBe(2);
    s.setMetricsRefreshRate(1);
    expect(useSettingsStore.getState().metricsRefreshRate).toBe(2);
  });

  it('clamps metrics history to [300..600]', () => {
    const s = useSettingsStore.getState();
    s.setMetricsHistory(100);
    expect(useSettingsStore.getState().metricsHistory).toBe(300);
    s.setMetricsHistory(9999);
    expect(useSettingsStore.getState().metricsHistory).toBe(600);
  });

  it('clamps max text-field chars to [1000..30000]', () => {
    const s = useSettingsStore.getState();
    s.setMaxTextFieldChars(10);
    expect(useSettingsStore.getState().maxTextFieldChars).toBe(1000);
    s.setMaxTextFieldChars(99999);
    expect(useSettingsStore.getState().maxTextFieldChars).toBe(30000);
  });
});
