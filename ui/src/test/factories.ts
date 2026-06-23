import type { Message } from '@/types';

/** Build a Message with sensible defaults; override any field per test. */
export function makeMessage(overrides: Partial<Message> = {}): Message {
  return {
    exchangeId: 'ex-1',
    exchangeEventType: 'SENDING',
    messageBody: 'body',
    headers: 'headers',
    routeId: 'route1',
    endpoint: 'direct:next',
    endpointId: 'out-1',
    messageType: 'REQUEST',
    exception: null,
    timeStamp: '2024-01-01T00:00:00Z',
    ...overrides,
  };
}
