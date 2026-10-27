import { cn } from '@client/src/lib/utils';
import { useEffect, useRef } from 'react';

const HEIGHT_CSS_VAR = '--sticky-header-height';

interface StickyHeaderProps {
  title: string;
  actions?: React.ReactNode;
  scrollRef?: React.RefObject<HTMLDivElement | null>;
  threshold?: number;
  className?: string;
  onMaskChange?: (masked: boolean) => void;
}

export function StickyHeader({
  title,
  actions,
  scrollRef,
  threshold = 24,
  className,
  onMaskChange,
}: StickyHeaderProps) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const heroRef = useRef<HTMLDivElement | null>(null);
  const compactRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    const updateHeight = () => {
      const h = root.offsetHeight;
      document.documentElement.style.setProperty(HEIGHT_CSS_VAR, `${h}px`);
    };
    updateHeight();
    const ro = new ResizeObserver(updateHeight);
    ro.observe(root);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    let disposed = false;
    let initRafId: number | null = null;
    let teardown: (() => void) | null = null;

    const getScrollEl = (): HTMLElement | null => {
      if (scrollRef?.current) return scrollRef.current;
      const found = root.closest('[class*="overflow-y"]');
      return found as HTMLElement | null;
    };

    const setup = (el: HTMLElement) => {
      let rafId: number | null = null;
      let lastCollapsed = false;

      const setCollapsed = (collapsed: boolean) => {
        if (collapsed === lastCollapsed) return;
        lastCollapsed = collapsed;
        if (collapsed) {
          root.classList.add('sticky-header--collapsed');
          root.classList.add('sticky-header--mask');
        } else {
          root.classList.remove('sticky-header--collapsed');
          root.classList.remove('sticky-header--mask');
        }
        onMaskChange?.(collapsed);
      };

      const onScroll = () => {
        if (rafId != null) return;
        rafId = requestAnimationFrame(() => {
          rafId = null;
          const top = (el as HTMLElement).scrollTop;
          setCollapsed(top > threshold);
        });
      };

      onScroll();
      el.addEventListener('scroll', onScroll, { passive: true });

      return () => {
        el.removeEventListener('scroll', onScroll);
        if (rafId != null) cancelAnimationFrame(rafId);
      };
    };

    const tryInit = () => {
      if (disposed) return;
      const el = getScrollEl();
      if (el) {
        teardown = setup(el);
        return;
      }
      initRafId = requestAnimationFrame(tryInit);
    };

    tryInit();

    return () => {
      disposed = true;
      if (initRafId != null) cancelAnimationFrame(initRafId);
      if (teardown) teardown();
    };
  }, [scrollRef, threshold]);

  return (
    <div
      ref={rootRef}
        className={cn(
         'sticky-header pointer-events-auto w-full pb-5 pt-3 sm:pb-6 sm:pt-4',
         className
       )}
    >
      <div
        ref={heroRef}
        className="sticky-header__hero flex items-start justify-between gap-3 pt-0.5"
      >
        <div className="min-w-0 flex-1">
            <h1 className="truncate text-xl font-bold leading-tight tracking-tight text-foreground sm:text-2xl">
              {title}
            </h1>
          </div>
        {actions && <div className="flex items-center gap-2">{actions}</div>}
      </div>

      <div
        ref={compactRef}
        className="sticky-header__compact flex items-center justify-center gap-2 pointer-events-none"
      >
        <span className="truncate text-[17px] font-semibold text-foreground">
          {title}
        </span>
      </div>
    </div>
  );
}
