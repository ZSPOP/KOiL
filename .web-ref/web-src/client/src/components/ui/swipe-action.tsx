import {
  useState,
  useRef,
  useCallback,
  useEffect,
  type ReactNode,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { motion, useMotionValue, useTransform, AnimatePresence, animate } from 'framer-motion';

interface SwipeActionProps {
  children: ReactNode;
  actions: {
    key: string;
    label: string;
    icon?: ReactNode;
    variant?: 'primary' | 'danger';
    onClick: () => void;
    'data-testid'?: string;
  }[];
  onOpenChange?: (open: boolean) => void;
  open: boolean;
}

const THRESHOLD = 60;
const LONG_PRESS_MS = 500;
const MOVE_THRESHOLD = 8;
const MAX_DRAG_OFFSET = 40;

export function SwipeAction({
  children,
  actions,
  onOpenChange,
  open,
}: SwipeActionProps) {
  const x = useMotionValue(0);
  const dragProgress = useTransform(x, [-THRESHOLD, 0], [1, 0]);
  const contentScale = useTransform(dragProgress, (p) => 1 - 0.02 * p);
  const contentRef = useRef<HTMLDivElement>(null);
  const longPressTimerRef = useRef<number | null>(null);
  const isDraggingRef = useRef(false);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const pointerIdRef = useRef<number | null>(null);
  const hasMovedRef = useRef(false);
  const longPressTriggeredRef = useRef(false);
  const [isAnimating, setIsAnimating] = useState(false);

  const animateTo = useCallback(
    (target: number) => {
      setIsAnimating(true);
      animate(x, target, {
        type: 'spring',
        stiffness: 500,
        damping: 40,
        mass: 0.6,
        onComplete: () => {
          setIsAnimating(false);
        },
      });
    },
    [x],
  );

  useEffect(() => {
    if (isAnimating) return;
    if (open) {
      x.set(0);
    } else {
      animateTo(0);
    }
  }, [open, animateTo, isAnimating, x]);

  const clearLongPress = useCallback(() => {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  }, []);

  const handlePointerDown = useCallback(
    (e: ReactPointerEvent<HTMLDivElement>) => {
      if (e.button !== 0 && e.pointerType === 'mouse') return;
      pointerIdRef.current = e.pointerId;
      startXRef.current = e.clientX;
      startYRef.current = e.clientY;
      isDraggingRef.current = false;
      hasMovedRef.current = false;
      longPressTriggeredRef.current = false;

      clearLongPress();
      longPressTimerRef.current = window.setTimeout(() => {
        if (!hasMovedRef.current && pointerIdRef.current !== null) {
          longPressTriggeredRef.current = true;
          onOpenChange?.(true);
          if (contentRef.current) {
            try {
              contentRef.current.releasePointerCapture(pointerIdRef.current);
            } catch {
              // ignore
            }
          }
          pointerIdRef.current = null;
        }
      }, LONG_PRESS_MS);
    },
    [onOpenChange, clearLongPress],
  );

  const handlePointerMove = useCallback(
    (e: ReactPointerEvent<HTMLDivElement>) => {
      if (pointerIdRef.current === null) return;
      if (longPressTriggeredRef.current) return;

      const dx = e.clientX - startXRef.current;
      const dy = e.clientY - startYRef.current;

      if (
        !hasMovedRef.current &&
        (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD)
      ) {
        hasMovedRef.current = true;
        clearLongPress();
      }

      if (Math.abs(dy) > Math.abs(dx) * 1.2 && !isDraggingRef.current) {
        return;
      }

      if (Math.abs(dx) > MOVE_THRESHOLD && Math.abs(dx) >= Math.abs(dy)) {
        isDraggingRef.current = true;
        try {
          (e.currentTarget as HTMLDivElement).setPointerCapture(e.pointerId);
        } catch {
          // ignore
        }
      }

      if (isDraggingRef.current) {
        const baseX = open ? -MAX_DRAG_OFFSET * 0.3 : 0;
        let nextX = dx + baseX;
        if (nextX > 0) nextX = 0;
        if (nextX < -MAX_DRAG_OFFSET) nextX = -MAX_DRAG_OFFSET;
        x.jump(nextX);
      }
    },
    [open, x, clearLongPress],
  );

  const handlePointerUp = useCallback(() => {
    if (longPressTriggeredRef.current) {
      longPressTriggeredRef.current = false;
      pointerIdRef.current = null;
      return;
    }
    if (pointerIdRef.current === null) return;

    clearLongPress();
    const currentX = x.get();

    if (isDraggingRef.current) {
      if (!open && currentX < -THRESHOLD) {
        onOpenChange?.(true);
      } else if (open) {
        onOpenChange?.(false);
      } else {
        animateTo(0);
      }
    }

    isDraggingRef.current = false;
    pointerIdRef.current = null;
  }, [x, onOpenChange, open, animateTo, clearLongPress]);

  const handlePointerCancel = useCallback(() => {
    clearLongPress();
    isDraggingRef.current = false;
    pointerIdRef.current = null;
    longPressTriggeredRef.current = false;
    animateTo(0);
  }, [clearLongPress, animateTo]);

  useEffect(() => {
    return () => clearLongPress();
  }, [clearLongPress]);

  const handleOverlayClick = useCallback(() => {
    onOpenChange?.(false);
  }, [onOpenChange]);

  const handleActionClick = useCallback(
    (action: { onClick: () => void }) => {
      onOpenChange?.(false);
      action.onClick();
    },
    [onOpenChange],
  );

  return (
    <div
      className="relative overflow-clip rounded-[28px]"
      style={{ overflowClipMargin: '24px' }}
      data-swipe-open={open ? 'true' : 'false'}
    >
      <motion.div
        ref={contentRef}
        style={{ x, scale: open ? 0.98 : contentScale }}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerCancel}
        onContextMenu={(e) => e.preventDefault()}
        className="touch-pan-y select-none will-change-transform"
      >
        {children}
      </motion.div>

      <AnimatePresence>
        {open && (
          <motion.button
            type="button"
            key="overlay"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={handleOverlayClick}
            className="absolute inset-0 z-10 rounded-[28px] bg-background/40 backdrop-blur-[2px]"
            aria-label="关闭操作"
          >
            <motion.div
              initial={{ opacity: 0, y: 8, scale: 0.9 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.9 }}
              transition={{ type: 'spring', stiffness: 400, damping: 28, mass: 0.5 }}
              className="flex h-full w-full items-center justify-center gap-8"
              onClick={(e) => e.stopPropagation()}
            >
              {actions.map((action) => (
                <button
                  key={action.key}
                  type="button"
                  data-testid={action['data-testid']}
                  onClick={() => handleActionClick(action)}
                  className={`group relative flex flex-col items-center gap-1.5 ${
                    action.variant === 'danger' ? 'text-destructive' : 'text-primary'
                  }`}
                >
                  <span
                    className={`flex size-12 items-center justify-center rounded-full text-white shadow-lg transition-transform sm:size-14 ${
                      action.variant === 'danger'
                        ? 'bg-destructive shadow-destructive/30'
                        : 'bg-primary shadow-black/30'
                    }`}
                  >
                    {action.icon}
                  </span>
                  <span className="text-[11px] font-medium text-foreground">
                    {action.label}
                  </span>
                </button>
              ))}
            </motion.div>
          </motion.button>
        )}
      </AnimatePresence>
    </div>
  );
}
