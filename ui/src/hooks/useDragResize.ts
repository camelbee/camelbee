import { useCallback, useEffect, useRef } from 'react';

/** Keyboard resize step, for when the grip is focused rather than dragged. */
const RESIZE_STEP_PX = 24;

export interface DragResizeOptions {
  /** Current size in px, owned by the caller (a store, so it survives remounting). */
  size: number;
  /** Persist a new size. Expected to clamp to its own absolute range. */
  setSize: (size: number) => void;
  /**
   * Which edge the panel is docked against. Both grow when dragged towards the page centre, so the
   * delta is the same in either case - only the coordinate and the viewport dimension differ.
   */
  axis: 'vertical' | 'horizontal';
  /** Px of the rest of the page to keep visible however far the grip is dragged. */
  minRemaining: number;
}

/**
 * Drag-to-resize behaviour for a docked panel, shared by the waterfall (bottom) and message (right)
 * panels.
 *
 * <p>Two details are deliberate. Listeners go on `window` rather than the grip, because a fast drag
 * outruns the pointer and a grip-local listener would silently stop resizing the moment the cursor
 * left its few pixels. And the viewport clamp lives here rather than in the store, because a store
 * has no idea how large the window is - it can only enforce an absolute range.
 */
export function useDragResize({ size, setSize, axis, minRemaining }: DragResizeOptions) {
  /** Null while idle; holds where the drag began so the panel tracks the pointer exactly. */
  const dragRef = useRef<{ start: number; startSize: number } | null>(null);

  const applySize = useCallback(
    (next: number) => {
      if (typeof window === 'undefined') {
        setSize(next);
        return;
      }
      const viewport = axis === 'vertical' ? window.innerHeight : window.innerWidth;
      setSize(Math.min(next, viewport - minRemaining));
    },
    [axis, minRemaining, setSize],
  );

  useEffect(() => {
    const onMove = (event: MouseEvent) => {
      const drag = dragRef.current;
      if (!drag) {
        return;
      }
      // dragging towards the page centre is a smaller coordinate and a bigger panel
      const current = axis === 'vertical' ? event.clientY : event.clientX;
      applySize(drag.startSize + (drag.start - current));
    };
    const onUp = () => {
      dragRef.current = null;
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [applySize, axis]);

  const onMouseDown = (event: React.MouseEvent) => {
    event.preventDefault();
    dragRef.current = {
      start: axis === 'vertical' ? event.clientY : event.clientX,
      startSize: size,
    };
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    const grow = axis === 'vertical' ? 'ArrowUp' : 'ArrowLeft';
    const shrink = axis === 'vertical' ? 'ArrowDown' : 'ArrowRight';

    if (event.key === grow) {
      event.preventDefault();
      applySize(size + RESIZE_STEP_PX);
    } else if (event.key === shrink) {
      event.preventDefault();
      applySize(size - RESIZE_STEP_PX);
    }
  };

  return { onMouseDown, onKeyDown };
}
