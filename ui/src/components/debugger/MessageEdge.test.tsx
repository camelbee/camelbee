import { describe, it, expect, vi, afterEach } from 'vitest';
import { render } from '@testing-library/react';
import { Position } from '@xyflow/react';
import type { EdgeProps } from '@xyflow/react';
import type { ReactNode } from 'react';
import { MessageEdge } from './MessageEdge';
import type { MessageEdgeData } from '@/utils/routeGraph';

function Svg({ children }: { children: ReactNode }) {
  return <svg>{children}</svg>;
}

function edgeProps(data: Partial<MessageEdgeData> | undefined, overrides: Partial<EdgeProps> = {}) {
  return {
    id: 'e1',
    source: 'a',
    target: 'b',
    sourceX: 0,
    sourceY: 0,
    targetX: 200,
    targetY: 100,
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    selected: false,
    data: data as MessageEdgeData | undefined,
    ...overrides,
  } as unknown as EdgeProps & { data: MessageEdgeData | undefined };
}

function baseData(over: Partial<MessageEdgeData> = {}): MessageEdgeData {
  return {
    outputId: 'o1',
    sourceRouteId: 'r1',
    messageCount: 0,
    hasError: false,
    animated: false,
    isErrorHandler: false,
    activeFlows: [],
    ...over,
  };
}

describe('MessageEdge', () => {
  it('renders a plain edge path', () => {
    const { container } = render(<MessageEdge {...edgeProps(baseData())} />, { wrapper: Svg });
    expect(container.querySelector('path')).toBeInTheDocument();
  });

  it('renders a message-count badge when there are messages', () => {
    const { getByText } = render(<MessageEdge {...edgeProps(baseData({ messageCount: 5 }))} />, {
      wrapper: Svg,
    });
    expect(getByText('5')).toBeInTheDocument();
  });

  it('renders without data, as an error-handler, selected, and animated', () => {
    expect(() =>
      render(<MessageEdge {...edgeProps(undefined)} />, { wrapper: Svg }),
    ).not.toThrow();
    expect(() =>
      render(<MessageEdge {...edgeProps(baseData({ isErrorHandler: true }), { selected: true })} />, {
        wrapper: Svg,
      }),
    ).not.toThrow();
    expect(() =>
      render(<MessageEdge {...edgeProps(baseData({ hasError: true, messageCount: 1, animated: true }))} />, {
        wrapper: Svg,
      }),
    ).not.toThrow();
  });

  describe('animated flow dots', () => {
    afterEach(() => {
      vi.unstubAllGlobals();
    });

    it('renders a flow dot with its label', () => {
      // No-op rAF: the dot mounts and stays (animation never completes).
      vi.stubGlobal('requestAnimationFrame', () => 1);
      vi.stubGlobal('cancelAnimationFrame', () => {});
      const { getByText } = render(
        <MessageEdge
          {...edgeProps(
            baseData({ activeFlows: [{ id: 1, type: 'REQUEST', label: 'REQ' }] }),
          )}
        />,
        { wrapper: Svg },
      );
      expect(getByText('REQ')).toBeInTheDocument();
    });

    it('handles response and error-response flow directions', () => {
      // Completing rAF: drives the bezier math + onDone cleanup to completion.
      vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
        cb(performance.now() + 10_000);
        return 1;
      });
      vi.stubGlobal('cancelAnimationFrame', () => {});
      expect(() =>
        render(
          <MessageEdge
            {...edgeProps(
              baseData({
                activeFlows: [
                  { id: 2, type: 'RESPONSE', label: 'RES' },
                  { id: 3, type: 'ERROR_RESPONSE', label: 'ERR' },
                ],
              }),
            )}
          />,
          { wrapper: Svg },
        ),
      ).not.toThrow();
    });
  });
});
