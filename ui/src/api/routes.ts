import { useQuery } from '@tanstack/react-query';
import type { CamelBeeContext } from '../types';
import { apiFetch } from './client';

/** GET /camelbee/routes — fetch the full route topology */
function fetchRoutes(): Promise<CamelBeeContext> {
  return apiFetch<CamelBeeContext>('/camelbee/routes');
}

export function useRoutes() {
  return useQuery({
    queryKey: ['routes'],
    queryFn: fetchRoutes,
    staleTime: Infinity,
  });
}
