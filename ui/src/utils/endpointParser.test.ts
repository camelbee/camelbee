import { describe, it, expect } from 'vitest';
import type { CamelRouteOutput } from '@/types';
import {
  extractComponentType,
  extractInputUri,
  extractStaticEndpointsFromOutput,
  outputReferencesInput,
} from './endpointParser';

function output(partial: Partial<CamelRouteOutput>): CamelRouteOutput {
  return {
    id: 'o1',
    description: '',
    delimiter: null,
    type: 'org.apache.camel.model.ToDefinition',
    outputs: [],
    ...partial,
  };
}

describe('extractComponentType', () => {
  it('returns the scheme before the first colon', () => {
    expect(extractComponentType('kafka:topic')).toBe('kafka');
    expect(extractComponentType('http://host/path')).toBe('http');
  });

  it('lowercases and falls back to the whole string when no colon', () => {
    expect(extractComponentType('Direct')).toBe('direct');
  });
});

describe('extractInputUri', () => {
  it('unwraps From[...]', () => {
    expect(extractInputUri('From[direct:myRoute]')).toBe('direct:myRoute');
  });

  it('returns the input unchanged when not wrapped', () => {
    expect(extractInputUri('direct:myRoute')).toBe('direct:myRoute');
  });
});

describe('extractStaticEndpointsFromOutput', () => {
  it('extracts a single external To[] target', () => {
    expect(extractStaticEndpointsFromOutput(output({ description: 'To[kafka:orders]' }))).toEqual([
      'kafka:orders',
    ]);
  });

  it('ignores internal direct:/seda: targets', () => {
    expect(extractStaticEndpointsFromOutput(output({ description: 'To[direct:next]' }))).toBeNull();
  });

  it('unwraps DynamicTo[toD[...]]', () => {
    expect(
      extractStaticEndpointsFromOutput(output({ description: 'DynamicTo[toD[http://api/x]]' })),
    ).toEqual(['http://api/x']);
  });

  it('splits RecipientList by delimiter and keeps only external endpoints', () => {
    const result = extractStaticEndpointsFromOutput(
      output({
        description: 'RecipientList[recipientList[{kafka:a,direct:b,http://c}]]',
        delimiter: ',',
      }),
    );
    expect(result).toEqual(['kafka:a', 'http://c']);
  });

  it('returns null for outputs with no endpoint', () => {
    expect(extractStaticEndpointsFromOutput(output({ description: 'Log[hello]' }))).toBeNull();
  });

  // Roadmap #22 (poll() extraction): Poll[uri] is matched the same way as
  // To[uri] — README-camel421-notes.md, FINAL ROADMAP v2.
  it('extracts a single external Poll[] target', () => {
    expect(
      extractStaticEndpointsFromOutput(
        output({ description: 'Poll[kafka:orders]', type: 'org.apache.camel.model.PollDefinition' }),
      ),
    ).toEqual(['kafka:orders']);
  });

  it('ignores internal direct:/seda: Poll[] targets', () => {
    expect(
      extractStaticEndpointsFromOutput(
        output({ description: 'Poll[direct:next]', type: 'org.apache.camel.model.PollDefinition' }),
      ),
    ).toBeNull();
  });
});

describe('outputReferencesInput', () => {
  it('matches a direct To[] reference', () => {
    expect(outputReferencesInput(output({ description: 'To[direct:next]' }), 'direct:next')).toBe(
      true,
    );
  });

  it('matches an input contained in a RecipientList', () => {
    expect(
      outputReferencesInput(
        output({ description: 'RecipientList[recipientList[{direct:a,direct:b}]]' }),
        'direct:a',
      ),
    ).toBe(true);
  });

  it('does not match an unrelated endpoint', () => {
    expect(outputReferencesInput(output({ description: 'To[direct:other]' }), 'direct:next')).toBe(
      false,
    );
  });

  // Roadmap #22 (poll() extraction).
  it('matches a direct Poll[] reference', () => {
    expect(
      outputReferencesInput(
        output({ description: 'Poll[direct:next]', type: 'org.apache.camel.model.PollDefinition' }),
        'direct:next',
      ),
    ).toBe(true);
  });

  // Roadmap #3 (query-param-proof edge matching): query params are common on
  // one side of a to/from pair but not the other, and don't change
  // direct:/seda: identity.
  it('matches when the output has query params but the input does not', () => {
    expect(
      outputReferencesInput(output({ description: 'To[direct:x?block=false]' }), 'direct:x'),
    ).toBe(true);
  });

  it('matches when the input has query params but the output does not', () => {
    expect(
      outputReferencesInput(output({ description: 'To[direct:x]' }), 'direct:x?bridgeErrorHandler=true'),
    ).toBe(true);
  });

  it('matches when both sides have different query params', () => {
    expect(
      outputReferencesInput(
        output({ description: 'To[direct:x?block=false]' }),
        'direct:x?bridgeErrorHandler=true',
      ),
    ).toBe(true);
  });

  it('still does not match a different path even when query params are stripped', () => {
    expect(
      outputReferencesInput(output({ description: 'To[direct:x?block=false]' }), 'direct:y'),
    ).toBe(false);
  });

  it('matches query params through the DynamicTo[toD[...]] unwrap', () => {
    expect(
      outputReferencesInput(
        output({ description: 'DynamicTo[toD[direct:x?block=false]]' }),
        'direct:x',
      ),
    ).toBe(true);
  });

  it('matches a RecipientList entry with query params, case-insensitively', () => {
    expect(
      outputReferencesInput(
        output({
          description: 'RECIPIENTLIST[recipientList[{direct:a?block=false,direct:b}]]',
          delimiter: ',',
        }),
        'direct:a',
      ),
    ).toBe(true);
  });
});
