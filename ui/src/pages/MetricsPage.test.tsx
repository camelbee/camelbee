import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { MetricsPage } from './MetricsPage';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installApiMock } from '@/test/mockApi';
import { useMetricsStore } from '@/store/metricsStore';

describe('MetricsPage', () => {
  beforeEach(() => {
    useMetricsStore.getState().clear();
    installApiMock();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('loads routes then renders the topology view with context info', async () => {
    renderWithProviders(<MetricsPage />);
    expect(screen.getByText('Loading routes…')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Topology')).toBeInTheDocument());
    expect(screen.getByText('Charts')).toBeInTheDocument();
    expect(screen.getByText('show all metrics')).toBeInTheDocument();
  });

  it('switches to the charts view', async () => {
    renderWithProviders(<MetricsPage />);
    await waitFor(() => expect(screen.getByText('Charts')).toBeInTheDocument());
    fireEvent.click(screen.getByText('Charts'));
    expect(screen.getByText('CPU Usage')).toBeInTheDocument();
  });

  it('opens the metrics detail modal', async () => {
    renderWithProviders(<MetricsPage />);
    await waitFor(() => expect(screen.getByText('show all metrics')).toBeInTheDocument());
    fireEvent.click(screen.getByText('show all metrics'));
    expect(screen.getByText('METRICS')).toBeInTheDocument();
  });
});
