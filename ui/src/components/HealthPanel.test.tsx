import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { HealthPanel } from './HealthPanel';
import type { HealthResponse } from '@/api';
import type { CamelBeeContext } from '@/types';

const health: HealthResponse = {
  status: 'UP',
  checks: [{ name: 'camel', status: 'UP' }],
};

const context = {
  name: 'my-service',
  framework: 'Quarkus',
  camelVersion: '4.20.0',
  jvm: 'OpenJDK 21',
  garbageCollectors: 'G1',
} as unknown as CamelBeeContext;

describe('HealthPanel', () => {
  it('renders a status indicator and expands to show context details', () => {
    render(<HealthPanel context={context} health={health} />);
    const toggle = screen.getByTitle('Status: UP');
    fireEvent.click(toggle);
    expect(screen.getByText('my-service')).toBeInTheDocument();
    expect(screen.getByText('Quarkus')).toBeInTheDocument();
    expect(screen.getByText('4.20.0')).toBeInTheDocument();
  });

  it('opens and closes the health detail modal', () => {
    render(<HealthPanel context={context} health={health} />);
    fireEvent.click(screen.getByTitle('Status: UP'));
    fireEvent.click(screen.getByText('View Health Details'));
    expect(screen.getByText('HEALTH')).toBeInTheDocument();
    fireEvent.click(screen.getByText('✕'));
    expect(screen.queryByText('HEALTH')).not.toBeInTheDocument();
  });

  it('shows a neutral indicator when health is unknown', () => {
    render(<HealthPanel />);
    expect(screen.getByText('?')).toBeInTheDocument();
  });

  it('reflects a DOWN status', () => {
    render(<HealthPanel health={{ status: 'DOWN', checks: [] }} />);
    expect(screen.getByTitle('Status: DOWN')).toBeInTheDocument();
    expect(screen.getByText('✕')).toBeInTheDocument();
  });
});
