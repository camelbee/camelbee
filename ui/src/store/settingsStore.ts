import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface SettingsState {
  theme: 'light' | 'dark';
  healthUrl: string;
  healthRefreshRate: number; // seconds [2..10]
  metricsUrl: string;
  metricsHistory: number; // seconds [300..600]
  metricsRefreshRate: number; // seconds [2..10]
  maxTextFieldChars: number; // [1000..30000]
  waterfallHeight: number; // px [140..900]
  messagePanelWidth: number; // px [280..1000]

  setTheme: (theme: 'light' | 'dark') => void;
  setHealthUrl: (url: string) => void;
  setHealthRefreshRate: (rate: number) => void;
  setMetricsUrl: (url: string) => void;
  setMetricsHistory: (history: number) => void;
  setMetricsRefreshRate: (rate: number) => void;
  setMaxTextFieldChars: (chars: number) => void;
  setWaterfallHeight: (height: number) => void;
  setMessagePanelWidth: (width: number) => void;
}

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      theme: 'light',
      healthUrl: '/health',
      healthRefreshRate: 5,
      metricsUrl: '/metrics',
      metricsHistory: 300,
      metricsRefreshRate: 5,
      maxTextFieldChars: 10000,
      waterfallHeight: 256,
      messagePanelWidth: 400,

      setTheme: (theme) => set({ theme }),
      setHealthUrl: (healthUrl) => set({ healthUrl }),
      setHealthRefreshRate: (healthRefreshRate) =>
        set({ healthRefreshRate: Math.min(10, Math.max(2, healthRefreshRate)) }),
      setMetricsUrl: (metricsUrl) => set({ metricsUrl }),
      setMetricsHistory: (metricsHistory) =>
        set({ metricsHistory: Math.min(600, Math.max(300, metricsHistory)) }),
      setMetricsRefreshRate: (metricsRefreshRate) =>
        set({ metricsRefreshRate: Math.min(10, Math.max(2, metricsRefreshRate)) }),
      setMaxTextFieldChars: (maxTextFieldChars) =>
        set({ maxTextFieldChars: Math.min(30000, Math.max(1000, maxTextFieldChars)) }),
      setWaterfallHeight: (waterfallHeight) =>
        set({ waterfallHeight: Math.min(900, Math.max(140, Math.round(waterfallHeight))) }),
      setMessagePanelWidth: (messagePanelWidth) =>
        set({ messagePanelWidth: Math.min(1000, Math.max(280, Math.round(messagePanelWidth))) }),
    }),
    {
      name: 'camelbee-settings',
    },
  ),
);
