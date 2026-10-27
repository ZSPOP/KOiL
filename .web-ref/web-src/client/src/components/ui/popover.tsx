import * as React from "react"
import * as PopoverPrimitive from "@radix-ui/react-popover"

import { cn } from "@/lib/utils"

function useNoTransformPosition(
  ref: React.RefObject<HTMLElement | null>
) {
  React.useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const convertTransformToPosition = () => {
      const transform = el.style.transform;
      if (!transform || transform === "none") return;
      const match = transform.match(/translate3d\(([\-\d.]+)px,\s*([\-\d.]+)px/);
      if (!match) return;
      const x = parseFloat(match[1]);
      const y = parseFloat(match[2]);
      if (Number.isNaN(x) || Number.isNaN(y)) return;
      el.style.transform = "none";
      el.style.top = `${y}px`;
      el.style.left = `${x}px`;
    };

    convertTransformToPosition();

    const observer = new MutationObserver(() => {
      if (el.style.transform && el.style.transform !== "none") {
        convertTransformToPosition();
      }
    });
    observer.observe(el, { attributes: true, attributeFilter: ["style"] });
    return () => observer.disconnect();
  }, [ref]);
}

function Popover({
  ...props
}: React.ComponentProps<typeof PopoverPrimitive.Root>) {
  return <PopoverPrimitive.Root data-slot="popover" {...props} />
}

function PopoverTrigger({
  ...props
}: React.ComponentProps<typeof PopoverPrimitive.Trigger>) {
  return <PopoverPrimitive.Trigger data-slot="popover-trigger" {...props} />
}

const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentProps<typeof PopoverPrimitive.Content> & {
    container?: React.ComponentProps<typeof PopoverPrimitive.Portal>["container"];
    noPortal?: boolean;
  }
>(({ className, align = "center", sideOffset = 4, container, noPortal, ...props }, ref) => {
  const innerRef = React.useRef<HTMLDivElement>(null);
  const composedRef = React.useCallback(
    (node: HTMLDivElement | null) => {
      innerRef.current = node;
      if (typeof ref === "function") {
        ref(node);
      } else if (ref && typeof ref === "object") {
        (ref as React.MutableRefObject<HTMLDivElement | null>).current = node;
      }
    },
    [ref]
  );
  useNoTransformPosition(innerRef);

  const content = (
    <PopoverPrimitive.Content
      ref={composedRef}
      data-slot="popover-content"
      align={align}
      sideOffset={sideOffset}
      className={cn(
        "glass-popup text-popover-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 z-50 w-72 rounded-xl border p-4 shadow-md outline-hidden",
        className
      )}
      {...props}
    />
  );
  return noPortal ? content : (
    <PopoverPrimitive.Portal container={container}>{content}</PopoverPrimitive.Portal>
  );
});
PopoverContent.displayName = "PopoverContent";

function PopoverAnchor({
  ...props
}: React.ComponentProps<typeof PopoverPrimitive.Anchor>) {
  return <PopoverPrimitive.Anchor data-slot="popover-anchor" {...props} />
}

function PopoverClose({
  ...props
}: React.ComponentProps<typeof PopoverPrimitive.Close>) {
  return <PopoverPrimitive.Close data-slot="popover-close" {...props} />
}

export { Popover, PopoverTrigger, PopoverContent, PopoverAnchor, PopoverClose }
