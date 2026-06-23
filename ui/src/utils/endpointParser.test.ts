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
});
