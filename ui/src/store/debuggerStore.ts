import { create } from 'zustand';
import type { Message } from '@/types';

interface DebuggerState {
  /* Message accumulation */
  messages: Message[];
  addVersion: number;
  resetVersion: number;
  lastIndex: number;

  /* Timeline */
  timelineIndex: number;
  prevTimelineIndex: number;

  /* Filtering */
  filterText: string;
  filteredMessages: Message[];

  /* Controls */
  isTracing: boolean;
  selectedEdgeId: string | null;

  /* Actions */
  appendMessages: (
    newMessages: Message[],
    newAddVersion: number,
    newResetVersion: number,
  ) => void;
  setTimelineIndex: (index: number) => void;
  stepForward: () => void;
  stepBack: () => void;
  setFilterText: (text: string) => void;
  setTracing: (active: boolean) => void;
  selectEdge: (edgeId: string | null) => void;
  clearMessages: () => void;
}

function applyFilter(messages: Message[], text: string): Message[] {
  if (!text) return messages;
  const lower = text.toLowerCase();
  return messages.filter(
    (m) =>
      m.messageBody?.toLowerCase().includes(lower) ||
      m.headers?.toLowerCase().includes(lower),
  );
}

export const useDebuggerStore = create<DebuggerState>((set, get) => ({
  messages: [],
  addVersion: -1,
  resetVersion: -1,
  lastIndex: 0,
  timelineIndex: 0,
  prevTimelineIndex: 0,
  filterText: '',
  filteredMessages: [],
  isTracing: false,
  selectedEdgeId: null,

  appendMessages: (newMessages, newAddVersion, newResetVersion) => {
    const state = get();

    // If reset version changed, server cleared messages
    if (newResetVersion !== state.resetVersion && state.resetVersion !== -1) {
      const filtered = applyFilter(newMessages, state.filterText);
      set({
        messages: newMessages,
        addVersion: newAddVersion,
        resetVersion: newResetVersion,
        lastIndex: newMessages.length,
        filteredMessages: filtered,
        timelineIndex: filtered.length,
        prevTimelineIndex: 0,
      });
      return;
    }

    const merged = [...state.messages, ...newMessages];
    const filtered = applyFilter(merged, state.filterText);
    set({
      messages: merged,
      addVersion: newAddVersion,
      resetVersion: newResetVersion,
      lastIndex: merged.length,
      filteredMessages: filtered,
      timelineIndex: filtered.length,
      prevTimelineIndex: state.timelineIndex,
    });
  },

  setTimelineIndex: (index) => {
    set((s) => ({
      prevTimelineIndex: s.timelineIndex,
      timelineIndex: index,
    }));
  },

  stepForward: () => {
    set((s) => {
      const next = Math.min(s.timelineIndex + 1, s.filteredMessages.length);
      return { prevTimelineIndex: s.timelineIndex, timelineIndex: next };
    });
  },

  stepBack: () => {
    set((s) => {
      const prev = Math.max(s.timelineIndex - 1, 0);
      return { prevTimelineIndex: s.timelineIndex, timelineIndex: prev };
    });
  },

  setFilterText: (text) => {
    const state = get();
    const filtered = applyFilter(state.messages, text);
    set({
      filterText: text,
      filteredMessages: filtered,
      timelineIndex: filtered.length,
      prevTimelineIndex: 0,
    });
  },

  setTracing: (active) => set({ isTracing: active }),

  selectEdge: (edgeId) => set({ selectedEdgeId: edgeId }),

  clearMessages: () =>
    set({
      messages: [],
      filteredMessages: [],
      lastIndex: 0,
      timelineIndex: 0,
      prevTimelineIndex: 0,
      addVersion: -1,
      resetVersion: -1,
      selectedEdgeId: null,
    }),
}));
