import { describe, it, expect } from 'vitest';
import * as api from './index';

describe('api barrel', () => {
  it('re-exports the public hooks and helpers', () => {
    expect(api.useRoutes).toBeTypeOf('function');
    expect(api.useMessages).toBeTypeOf('function');
    expect(api.useTraceStatus).toBeTypeOf('function');
    expect(api.useDeleteMessages).toBeTypeOf('function');
    expect(api.useMetrics).toBeTypeOf('function');
    expect(api.parsePrometheus).toBeTypeOf('function');
    expect(api.useHealth).toBeTypeOf('function');
  });
});
