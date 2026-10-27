import { cn } from '@client/src/lib/utils';
import { useBounceScroll } from '@client/src/hooks/useBounceScroll';
import { forwardRef, useImperativeHandle, useRef } from 'react';

interface BounceScrollProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  damping?: number;
  maxBounceRatio?: number;
  bounceBackDuration?: number;
  scrollRef?: React.RefObject<HTMLDivElement | null>;
  onScroll?: (e: React.UIEvent<HTMLDivElement>) => void;
}

export const BounceScroll = forwardRef<HTMLDivElement, BounceScrollProps>(
  function BounceScroll(
    {
      children,
      className,
      damping,
      maxBounceRatio,
      bounceBackDuration,
      scrollRef: externalScrollRef,
      onScroll,
      ...props
    },
    ref
  ) {
    const { scrollRef, touchHandlers } = useBounceScroll<HTMLDivElement>({
      damping,
      maxBounceRatio,
      bounceBackDuration,
    });

    useImperativeHandle(ref, () => scrollRef.current as HTMLDivElement);

    useImperativeHandle(
      externalScrollRef as React.MutableRefObject<HTMLDivElement | null> | undefined,
      () => scrollRef.current,
      [scrollRef.current]
    );

    return (
      <div
        ref={scrollRef}
        className={cn(
          'h-full overflow-y-auto overflow-x-hidden scrollbar-none touch-pan-y',
          className
        )}
        style={{
          WebkitOverflowScrolling: 'touch',
        }}
        onScroll={onScroll}
        {...touchHandlers}
        {...props}
      >
        {children}
      </div>
    );
  }
);
