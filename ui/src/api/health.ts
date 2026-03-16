import { useQuery } from '@tanstack/react-query';
import { apiFetch } from './client';

export interface HealthCheck {
  name: string;
  status: 'UP' | 'DOWN';
  data?: Record<string, string>;
}

export interface HealthResponse {
  status: 'UP' | 'DOWN';
  checks: HealthCheck[];
}

function fetchHealth(url: string): Promise<HealthResponse | null> {
  return apiFetch<HealthResponse>(url).catch(() => null);
}

export function useHealth(enabled: boolean, refreshInterval: number, url = '/health') {
  return useQuery({
    queryKey: ['health', url],
    queryFn: () => fetchHealth(url),
    enabled,
    refetchInterval: enabled ? refreshInterval * 1000 : false,
    retry: false,
  });
}
