import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { MessageListWithInfo, TraceStatus } from '../types';
import { apiFetch } from './client';

/** GET /camelbee/messages — poll for traced messages */
function fetchMessages(
  index: number,
  addVersion: number,
  resetVersion: number,
): Promise<MessageListWithInfo> {
  const params = new URLSearchParams({
    index: String(index),
    addVersion: String(addVersion),
    resetVersion: String(resetVersion),
  });
  return apiFetch<MessageListWithInfo>(`/camelbee/messages?${params}`);
}

/**
 * Hook for polling messages. Caller manages version/index state and
 * passes them in. Polling is enabled via `enabled`.
 */
export function useMessages(
  index: number,
  addVersion: number,
  resetVersion: number,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['messages', index, addVersion, resetVersion],
    queryFn: () => fetchMessages(index, addVersion, resetVersion),
    enabled,
    refetchInterval: enabled ? 2000 : false,
  });
}

/** POST /camelbee/tracer/status — enable or disable tracing */
function updateTraceStatus(status: TraceStatus): Promise<string> {
  return apiFetch<string>('/camelbee/tracer/status', {
    method: 'POST',
    body: JSON.stringify(status),
  });
}

export function useTraceStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: updateTraceStatus,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['messages'] });
    },
  });
}

/** DELETE /camelbee/messages — clear all traced messages */
function deleteMessages(): Promise<string> {
  return apiFetch<string>('/camelbee/messages', { method: 'DELETE' });
}

export function useDeleteMessages() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: deleteMessages,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['messages'] });
    },
  });
}
