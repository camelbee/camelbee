import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SettingsPage } from './SettingsPage';
import { useSettingsStore } from '@/store/settingsStore';

const initial = useSettingsStore.getState();

describe('SettingsPage', () => {
  beforeEach(() => {
    useSettingsStore.setState(initial, true);
  });

  it('renders all settings rows', () => {
    render(<SettingsPage />);
    expect(screen.getByText('theme')).toBeInTheDocument();
    expect(screen.getByText('health url')).toBeInTheDocument();
    expect(screen.getByText('metrics url')).toBeInTheDocument();
    expect(screen.getByText('max characters in a text field')).toBeInTheDocument();
  });

  it('toggles the theme via the buttons', () => {
    render(<SettingsPage />);
    fireEvent.click(screen.getByText('Dark'));
    expect(useSettingsStore.getState().theme).toBe('dark');
    fireEvent.click(screen.getByText('Light'));
    expect(useSettingsStore.getState().theme).toBe('light');
  });

  it('updates the health and metrics URLs', () => {
    render(<SettingsPage />);
    fireEvent.change(screen.getByDisplayValue('/health'), { target: { value: '/q/health' } });
    expect(useSettingsStore.getState().healthUrl).toBe('/q/health');
    fireEvent.change(screen.getByDisplayValue('/metrics'), { target: { value: '/q/metrics' } });
    expect(useSettingsStore.getState().metricsUrl).toBe('/q/metrics');
  });

  it('clamps numeric inputs through the store setters', () => {
    render(<SettingsPage />);
    const history = screen.getByDisplayValue('300');
    fireEvent.change(history, { target: { value: '9999' } });
    expect(useSettingsStore.getState().metricsHistory).toBe(600);
  });
});
