import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { NavBar } from './NavBar';
import { renderWithProviders } from '@/test/renderWithProviders';

describe('NavBar', () => {
  it('renders the brand and all tabs', () => {
    renderWithProviders(<NavBar />, { route: '/debugger' });
    expect(screen.getByText('CAMEL BEE')).toBeInTheDocument();
    expect(screen.getByText('DEBUGGER')).toBeInTheDocument();
    expect(screen.getByText('METRICS')).toBeInTheDocument();
    expect(screen.getByText('SETTINGS')).toBeInTheDocument();
  });

  it('hides the BACK link on the debugger route', () => {
    renderWithProviders(<NavBar />, { route: '/debugger' });
    expect(screen.queryByText(/BACK/)).not.toBeInTheDocument();
  });

  it('shows the BACK link on other routes', () => {
    renderWithProviders(<NavBar />, { route: '/settings' });
    expect(screen.getByText(/BACK/)).toBeInTheDocument();
  });
});
