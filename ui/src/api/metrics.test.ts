import { describe, it, expect } from 'vitest';
import { parsePrometheus } from './metrics';

describe('parsePrometheus', () => {
  it('parses labelled and unlabelled metric lines', () => {
    const text = [
      '# HELP system_cpu_usage The CPU usage',
      '# TYPE system_cpu_usage gauge',
      'system_cpu_usage 0.42',
      'jvm_memory_used_bytes{area="heap",id="eden"} 1048576',
    ].join('\n');

    const metrics = parsePrometheus(text);
    expect(metrics).toEqual([
      { name: 'system_cpu_usage', value: 0.42 },
      { name: 'jvm_memory_used_bytes{area="heap",id="eden"}', value: 1048576 },
    ]);
  });

  it('skips comments, blank lines, and non-numeric values', () => {
    const text = ['', '# a comment', 'broken_line', 'good_metric 5'].join('\n');
    expect(parsePrometheus(text)).toEqual([{ name: 'good_metric', value: 5 }]);
  });

  it('returns an empty array for empty input', () => {
    expect(parsePrometheus('')).toEqual([]);
  });
});
