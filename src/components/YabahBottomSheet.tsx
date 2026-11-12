import React, { useRef, useState, useEffect, useCallback } from 'react';
import { SheetValue, resolveSheetTarget } from '../core/rules';

interface YabahBottomSheetLayoutProps {
  value: SheetValue;
  onChange: (next: SheetValue) => void;
  peekHeight?: number;
  halfFraction?: number;
  expandedTopInset?: number;
  handleDesc: string;
  expandLabel: string;
  collapseLabel: string;
  background: (visibleSheetHeight: number) => React.ReactNode;
  children: React.ReactNode;
}

export const YabahBottomSheetLayout: React.FC<YabahBottomSheetLayoutProps> = ({
  value,
  onChange,
  peekHeight = 212,
  halfFraction = 0.52,
  expandedTopInset = 72,
  handleDesc,
  background,
  children,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [containerHeight, setContainerHeight] = useState<number>(844);
  const [dragOffset, setDragOffset] = useState<number | null>(null);
  const dragStartRef = useRef<{ y: number; startOffset: number; lastY: number; lastTime: number; velocity: number } | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const update = () => {
      const h = el.clientHeight;
      if (h > 0) setContainerHeight(h);
    };
    update();
    const ro = new ResizeObserver(update);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const topPx = expandedTopInset;
  const clampedPeek = Math.min(peekHeight, containerHeight - topPx);
  const collapsedOffset = containerHeight - clampedPeek;
  const halfOffset = Math.max(
    topPx,
    Math.min(collapsedOffset, containerHeight * (1 - halfFraction))
  );

  const anchors: Record<SheetValue, number> = {
    [SheetValue.Expanded]: topPx,
    [SheetValue.Half]: halfOffset,
    [SheetValue.Collapsed]: collapsedOffset,
  };

  const currentOffset = dragOffset ?? anchors[value];
  const visibleHeight = Math.max(0, containerHeight - currentOffset);

  const handlePointerDown = useCallback(
    (e: React.PointerEvent) => {
      e.currentTarget.setPointerCapture(e.pointerId);
      dragStartRef.current = {
        y: e.clientY,
        startOffset: currentOffset,
        lastY: e.clientY,
        lastTime: performance.now(),
        velocity: 0,
      };
    },
    [currentOffset]
  );

  const handlePointerMove = useCallback(
    (e: React.PointerEvent) => {
      const drag = dragStartRef.current;
      if (!drag) return;
      const now = performance.now();
      const dt = Math.max(1, now - drag.lastTime);
      const dy = e.clientY - drag.lastY;
      drag.velocity = (dy / dt) * 1000;
      drag.lastY = e.clientY;
      drag.lastTime = now;

      const delta = e.clientY - drag.y;
      if (Math.abs(delta) > 4 || dragOffset !== null) {
        const next = Math.max(topPx, Math.min(collapsedOffset, drag.startOffset + delta));
        setDragOffset(next);
      }
    },
    [collapsedOffset, dragOffset, topPx]
  );

  const handlePointerUp = useCallback(() => {
    const drag = dragStartRef.current;
    if (!drag) return;
    dragStartRef.current = null;
    if (dragOffset !== null) {
      const target = resolveSheetTarget(anchors, dragOffset, drag.velocity);
      setDragOffset(null);
      onChange(target);
    }
  }, [anchors, dragOffset, onChange]);

  const cycleSheet = () => {
    if (dragOffset !== null) return;
    const next =
      value === SheetValue.Collapsed
        ? SheetValue.Half
        : value === SheetValue.Half
          ? SheetValue.Expanded
          : SheetValue.Collapsed;
    onChange(next);
  };

  return (
    <div
      ref={containerRef}
      data-testid="home_screen"
      className="relative w-full h-full overflow-hidden select-none"
    >
      {background(visibleHeight)}

      <div
        data-testid="trip_sheet"
        data-state={value}
        style={{
          height: `${Math.max(200, containerHeight - topPx)}px`,
          transform: `translateY(${Math.round(currentOffset)}px)`,
          transition: dragOffset === null ? 'transform 280ms cubic-bezier(0.22, 1, 0.36, 1)' : 'none',
        }}
        className="absolute inset-x-0 top-0 rounded-t-[28px] bg-white dark:bg-[#18263B] shadow-[0_-8px_30px_rgba(16,27,44,0.16)] flex flex-col overflow-hidden z-20"
      >
        {/* Grabber handle */}
        <button
          type="button"
          data-testid="sheet_handle"
          aria-label={handleDesc}
          onClick={cycleSheet}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={handlePointerUp}
          className="w-full h-12 shrink-0 flex items-center justify-center cursor-grab active:cursor-grabbing touch-none focus:outline-none"
        >
          <span className="w-10 h-[5px] rounded-full bg-[#B9C0CB] dark:bg-[#51617A]" />
        </button>

        {/* Sheet scrollable content */}
        <div className="flex-1 overflow-y-auto px-4 pb-6 space-y-2.5">
          {children}
        </div>
      </div>
    </div>
  );
};
