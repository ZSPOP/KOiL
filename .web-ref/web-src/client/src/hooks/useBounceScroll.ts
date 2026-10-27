import { useRef, useCallback, useEffect } from 'react';

interface UseBounceScrollOptions {
  damping?: number;
  maxBounceRatio?: number;
  bounceBackDuration?: number;
}

const DEFAULT_DAMPING = 0.4;
const DEFAULT_MAX_BOUNCE_RATIO = 0.18;
const DEFAULT_BOUNCE_BACK_DURATION = 400;

export function useBounceScroll<T extends HTMLElement = HTMLDivElement>(
  options: UseBounceScrollOptions = {}
) {
  const {
    damping = DEFAULT_DAMPING,
    maxBounceRatio = DEFAULT_MAX_BOUNCE_RATIO,
    bounceBackDuration = DEFAULT_BOUNCE_BACK_DURATION,
  } = options;

  const scrollRef = useRef<T | null>(null);
  const bounceYRef = useRef(0);
  const isBouncingRef = useRef(false);

  const touchStartYRef = useRef(0);
  const touchStartBounceRef = useRef(0);
  const isTouchingRef = useRef(false);
  const rafIdRef = useRef<number | null>(null);
  const pendingYRef = useRef<number | null>(null);

  const containerHRef = useRef(0);
  const maxBounceRef = useRef(0);

  const updateSizeCache = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    containerHRef.current = el.clientHeight;
    maxBounceRef.current = containerHRef.current * maxBounceRatio;
  }, [maxBounceRatio]);

  const applyBounceRaw = useCallback((y: number) => {
    bounceYRef.current = y;
    const el = scrollRef.current;
    if (!el) return;
    el.style.transform = `translate3d(0, ${y}px, 0)`;
    el.style.willChange = 'transform';
  }, []);

  const requestBounceFrame = useCallback((y: number) => {
    pendingYRef.current = y;
    if (rafIdRef.current != null) return;
    rafIdRef.current = requestAnimationFrame(() => {
      rafIdRef.current = null;
      if (pendingYRef.current != null) {
        applyBounceRaw(pendingYRef.current);
        pendingYRef.current = null;
      }
    });
  }, [applyBounceRaw]);

  const setBouncing = useCallback((val: boolean) => {
    if (isBouncingRef.current === val) return;
    isBouncingRef.current = val;
    const el = scrollRef.current;
    if (!el) return;
    if (val) {
      el.style.willChange = 'transform';
    } else {
      el.style.willChange = 'auto';
    }
  }, []);

  const animateBack = useCallback(() => {
    if (rafIdRef.current != null) {
      cancelAnimationFrame(rafIdRef.current);
      rafIdRef.current = null;
    }
    pendingYRef.current = null;

    const startY = bounceYRef.current;
    if (Math.abs(startY) < 0.5) {
      applyBounceRaw(0);
      setBouncing(false);
      return;
    }

    const startTime = performance.now();
    const duration = bounceBackDuration;
    const easeOutQuart = (t: number): number => 1 - Math.pow(1 - t, 4);

    const step = (now: number) => {
      const elapsed = now - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const eased = easeOutQuart(progress);
      const currentY = startY * (1 - eased);
      applyBounceRaw(currentY);

      if (progress < 1) {
        rafIdRef.current = requestAnimationFrame(step);
      } else {
        applyBounceRaw(0);
        setBouncing(false);
        if (scrollRef.current) {
          scrollRef.current.style.willChange = 'auto';
        }
        rafIdRef.current = null;
      }
    };

    rafIdRef.current = requestAnimationFrame(step);
  }, [bounceBackDuration, applyBounceRaw, setBouncing]);

  const handleTouchStart = useCallback((e: React.TouchEvent<T>) => {
    if (rafIdRef.current != null) {
      cancelAnimationFrame(rafIdRef.current);
      rafIdRef.current = null;
    }
    pendingYRef.current = null;
    updateSizeCache();
    isTouchingRef.current = true;
    touchStartYRef.current = e.touches[0].clientY;
    touchStartBounceRef.current = bounceYRef.current;
  }, [updateSizeCache]);

  const handleTouchMove = useCallback((e: React.TouchEvent<T>) => {
    if (!isTouchingRef.current || !scrollRef.current) return;

    const el = scrollRef.current;
    const touchY = e.touches[0].clientY;
    const deltaY = touchY - touchStartYRef.current;
    const maxBounce = maxBounceRef.current;

    const atTop = el.scrollTop <= 1;
    const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;

    const currentBounce = bounceYRef.current;

    if (currentBounce !== 0) {
      const newBounce = touchStartBounceRef.current + deltaY * damping;
      const clamped = Math.max(-maxBounce, Math.min(maxBounce, newBounce));
      if (Math.abs(clamped) > 0.5 || currentBounce !== 0) {
        requestBounceFrame(clamped);
        setBouncing(true);
      }
      e.preventDefault();
      return;
    }

    if (atTop && deltaY > 8) {
      const damped = (deltaY - 8) * damping;
      const clamped = Math.min(damped, maxBounce);
      requestBounceFrame(clamped);
      setBouncing(true);
      touchStartYRef.current = touchY - 8;
      touchStartBounceRef.current = 0;
      e.preventDefault();
    } else if (atBottom && deltaY < -8) {
      const damped = (deltaY + 8) * damping;
      const clamped = Math.max(damped, -maxBounce);
      requestBounceFrame(clamped);
      setBouncing(true);
      touchStartYRef.current = touchY + 8;
      touchStartBounceRef.current = 0;
      e.preventDefault();
    }
  }, [damping, requestBounceFrame, setBouncing]);

  const handleTouchEnd = useCallback(() => {
    if (!isTouchingRef.current) return;
    isTouchingRef.current = false;
    if (bounceYRef.current !== 0) {
      animateBack();
    }
  }, [animateBack]);

  const handleTouchCancel = useCallback(() => {
    isTouchingRef.current = false;
    if (bounceYRef.current !== 0) {
      animateBack();
    }
  }, [animateBack]);

  useEffect(() => {
    updateSizeCache();
    const el = scrollRef.current;
    if (!el) return;
    const ro = new ResizeObserver(updateSizeCache);
    ro.observe(el);
    return () => {
      ro.disconnect();
      if (rafIdRef.current != null) {
        cancelAnimationFrame(rafIdRef.current);
      }
    };
  }, [updateSizeCache]);

  return {
    scrollRef,
    touchHandlers: {
      onTouchStart: handleTouchStart,
      onTouchMove: handleTouchMove,
      onTouchEnd: handleTouchEnd,
      onTouchCancel: handleTouchCancel,
    },
  };
}
