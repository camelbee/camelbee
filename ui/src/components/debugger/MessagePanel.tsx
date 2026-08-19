import { useEffect, useMemo, useState } from 'react';
import { useDebuggerStore } from '@/store/debuggerStore';
import { useSettingsStore } from '@/store/settingsStore';
import { useDragResize } from '@/hooks/useDragResize';
import { buildInteractionsForEdge, type Interaction } from '@/utils/messageMatching';
import type { MessageEdge } from '@/utils/routeGraph';
import type { Message } from '@/types';

interface MessagePanelProps {
  edges: MessageEdge[];
}

/** Leave the graph this much room however far the panel is dragged. */
const MIN_GRAPH_PX = 240;

export function MessagePanel({ edges }: MessagePanelProps) {
  const selectedEdgeId = useDebuggerStore((s) => s.selectedEdgeId);
  const selectEdge = useDebuggerStore((s) => s.selectEdge);
  const filteredMessages = useDebuggerStore((s) => s.filteredMessages);
  const timelineIndex = useDebuggerStore((s) => s.timelineIndex);
  const setTimelineIndex = useDebuggerStore((s) => s.setTimelineIndex);

  const [interactionIdx, setInteractionIdx] = useState(0);

  const width = useSettingsStore((s) => s.messagePanelWidth);
  const setWidth = useSettingsStore((s) => s.setMessagePanelWidth);

  const { onMouseDown: onGripMouseDown, onKeyDown: onGripKeyDown } = useDragResize({
    size: width,
    setSize: setWidth,
    axis: 'horizontal',
    minRemaining: MIN_GRAPH_PX,
  });

  const edge = useMemo(
    () => edges.find((e) => e.id === selectedEdgeId),
    [edges, selectedEdgeId],
  );

  const slicedMessages = useMemo(
    () => filteredMessages.slice(0, timelineIndex),
    [filteredMessages, timelineIndex],
  );

  const interactions = useMemo(() => {
    if (!edge) return [];
    return buildInteractionsForEdge(slicedMessages, edge, edges);
  }, [edge, edges, slicedMessages]);

  // Reset interaction index when edge changes
  useEffect(() => {
    setInteractionIdx(Math.max(0, interactions.length - 1));
  }, [selectedEdgeId, interactions.length]);

  if (!selectedEdgeId || !edge) return null;

  const current: Interaction | undefined = interactions[interactionIdx];

  const handleGoToTimeline = () => {
    if (!current) return;
    // Find the index of the response (or request) in filteredMessages
    const target = current.response ?? current.request;
    if (!target) return;
    const idx = filteredMessages.indexOf(target);
    if (idx !== -1) setTimelineIndex(idx + 1);
  };

  return (
    <div
      className="relative flex h-full shrink-0 flex-col border-l border-gray-300 bg-gray-50 dark:border-gray-700 dark:bg-gray-900"
      style={{ width: `${width}px` }}
      data-testid="message-panel"
    >
      {/*
        Resize grip on the LEFT edge, since the panel is docked right. Absolutely positioned so it
        does not take a column out of the layout, and pulled slightly outside the border so the
        cursor changes a pixel before the edge rather than a pixel after it.
      */}
      <div
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize message panel"
        aria-valuenow={width}
        tabIndex={0}
        onMouseDown={onGripMouseDown}
        onKeyDown={onGripKeyDown}
        data-testid="message-panel-resize-handle"
        className="group absolute -left-1 top-0 z-10 flex h-full w-2 cursor-col-resize items-center justify-center focus:outline-none"
      >
        <span className="h-10 w-0.5 rounded-full bg-gray-300 group-hover:bg-blue-500 group-focus:bg-blue-500 dark:bg-gray-600" />
      </div>

      {/* Header */}
      <div className="flex items-center justify-between border-b border-gray-300 px-3 py-2 dark:border-gray-700">
        <span className="text-xs font-semibold text-gray-800 dark:text-gray-200">
          Messages ({interactions.length})
        </span>
        <button
          onClick={() => selectEdge(null)}
          aria-label="Close message panel"
          className="text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200"
        >
          ✕
        </button>
      </div>

      {/* Navigation */}
      {interactions.length > 1 && (
        <div className="flex items-center justify-between border-b border-gray-300 px-3 py-1 dark:border-gray-700">
          <button
            onClick={() => setInteractionIdx(Math.max(0, interactionIdx - 1))}
            disabled={interactionIdx === 0}
            className="text-xs text-gray-500 hover:text-gray-800 disabled:opacity-40 dark:text-gray-400 dark:hover:text-gray-200"
          >
            ◀ Prev
          </button>
          <span className="text-xs tabular-nums text-gray-500 dark:text-gray-400">
            {interactionIdx + 1} / {interactions.length}
          </span>
          <button
            onClick={() =>
              setInteractionIdx(
                Math.min(interactions.length - 1, interactionIdx + 1),
              )
            }
            disabled={interactionIdx >= interactions.length - 1}
            className="text-xs text-gray-500 hover:text-gray-800 disabled:opacity-40 dark:text-gray-400 dark:hover:text-gray-200"
          >
            Next ▶
          </button>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-y-auto px-3 py-2">
        {!current ? (
          <p className="text-xs text-gray-400 dark:text-gray-500">No messages for this edge at current timeline position.</p>
        ) : (
          <div className="space-y-3">
            {/* Status badge */}
            <div className="flex items-center gap-2">
              <span
                className={`rounded px-2 py-0.5 text-[10px] font-bold uppercase ${
                  current.isError
                    ? 'bg-red-500/20 text-red-600 dark:text-red-400'
                    : 'bg-green-500/20 text-green-600 dark:text-green-400'
                }`}
              >
                {current.isError ? 'Error' : 'Success'}
              </span>
              <span className="truncate text-[10px] text-gray-400 dark:text-gray-500">
                {current.exchangeId}
              </span>
              {!!current.response?.timeTaken && (
                <span className="ml-auto shrink-0 text-[10px] tabular-nums text-gray-400 dark:text-gray-500">
                  {current.response.timeTaken}ms
                </span>
              )}
            </div>

            {/* Request */}
            {current.request && (
              <MessageBlock label="Request" message={current.request} />
            )}

            {/* Response */}
            {current.response && (
              <MessageBlock label="Response" message={current.response} />
            )}

            {/* Exception */}
            {current.response?.exception && (
              <div>
                <h4 className="mb-1 text-[10px] font-semibold uppercase text-red-600 dark:text-red-400">
                  Exception
                </h4>
                <pre className="whitespace-pre-wrap rounded bg-red-500/10 p-2 text-[11px] text-red-700 dark:text-red-300">
                  {current.response.exception}
                </pre>
              </div>
            )}

            {/* Go to timeline */}
            <button
              onClick={handleGoToTimeline}
              className="w-full rounded bg-gray-200 py-1 text-xs text-gray-700 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
            >
              Go to timeline position
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function MessageBlock({ label, message }: { label: string; message: Message }) {
  return (
    <div>
      <h4 className="mb-1 text-[10px] font-semibold uppercase text-gray-500 dark:text-gray-400">
        {label}
      </h4>
      {message.headers && (
        <details className="mb-1">
          <summary className="cursor-pointer text-[10px] text-gray-400 dark:text-gray-500">
            Headers
          </summary>
          <pre className="mt-0.5 max-h-32 overflow-auto whitespace-pre-wrap rounded bg-gray-200 p-1.5 text-[11px] text-gray-700 dark:bg-gray-800 dark:text-gray-300">
            {message.headers}
          </pre>
        </details>
      )}
      {message.messageBody && (
        <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded bg-gray-200 p-2 text-[11px] text-gray-700 dark:bg-gray-800 dark:text-gray-300">
          {message.messageBody}
        </pre>
      )}
    </div>
  );
}