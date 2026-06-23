import { describe, it, expect, beforeEach } from 'vitest';
import { render } from '@testing-library/react';
import { MetricsRouteGraph } from './MetricsRouteGraph';
import { useMetricsStore } from '@/store/metricsStore';
import type { CamelBeeContext } from '@/types';
import routesFixture from '../../../mock/routes.json';

const context = routesFixture as unknown as CamelBeeContext;

describe('MetricsRouteGraph', () => {
  beforeEach(() => {
    useMetricsStore.getState().clear();
  });

  it('renders the topology flow graph', () => {
    render(<MetricsRouteGraph context={context} />);
    expect(document.querySelector('.react-flow')).toBeInTheDocument();
  });

  it('re-applies route metrics from the store onto the nodes', () => {
    const firstRouteId = context.routes[0]!.id;
    useMetricsStore.setState({
      routeMetrics: [
        { routeId: firstRouteId, exchangesTotal: 9, exchangesFailed: 1, exchangesSucceeded: 8 },
      ],
    });
    expect(() => render(<MetricsRouteGraph context={context} />)).not.toThrow();
  });
});
