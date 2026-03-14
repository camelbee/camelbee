import { useDebuggerStore } from '@/store/debuggerStore';

export function TimelineBar() {
  const timelineIndex = useDebuggerStore((s) => s.timelineIndex);
  const total = useDebuggerStore((s) => s.filteredMessages.length);
  const setTimelineIndex = useDebuggerStore((s) => s.setTimelineIndex);
  const stepBack = useDebuggerStore((s) => s.stepBack);
  const stepForward = useDebuggerStore((s) => s.stepForward);

  return (
    <div className="flex items-center gap-3 border-t border-gray-700 bg-gray-900 px-4 py-2">
      <button
        onClick={stepBack}
        disabled={timelineIndex <= 0}
        aria-label="Step back"
        className="rounded bg-gray-700 px-2 py-1 text-xs text-gray-300 hover:bg-gray-600 disabled:opacity-40"
      >
        ◀
      </button>

      <input
        type="range"
        min={0}
        max={total}
        value={timelineIndex}
        onChange={(e) => setTimelineIndex(Number(e.target.value))}
        aria-label="Timeline position"
        className="h-1.5 flex-1 cursor-pointer appearance-none rounded-full bg-gray-700 accent-blue-500"
      />

      <button
        onClick={stepForward}
        disabled={timelineIndex >= total}
        aria-label="Step forward"
        className="rounded bg-gray-700 px-2 py-1 text-xs text-gray-300 hover:bg-gray-600 disabled:opacity-40"
      >
        ▶
      </button>

      <span className="min-w-[80px] text-right text-xs tabular-nums text-gray-400">
        {timelineIndex} / {total}
      </span>
    </div>
  );
}
