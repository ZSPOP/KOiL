import { cn } from '@client/src/lib/utils';

interface AppTabNavProps {
  tabs: {
    key: string;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
  }[];
  activeKey: string;
  onChange: (key: string) => void;
  visible?: boolean;
  variant?: 'bottom-bar' | 'side-rail';
  className?: string;
  style?: React.CSSProperties;
}

export function AppTabNav({
  tabs,
  activeKey,
  onChange,
  visible = true,
  variant = 'bottom-bar',
  className,
  style,
}: AppTabNavProps) {
  const isRail = variant === 'side-rail';

  return (
    <nav
      className={cn(
        'app-tab-nav pointer-events-auto',
        isRail && 'app-tab-nav--rail',
        !visible && 'pointer-events-none opacity-0',
        className
      )}
      style={style}
      aria-label="主导航"
    >
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = tab.key === activeKey;
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => onChange(tab.key)}
            aria-current={isActive ? 'page' : undefined}
            aria-label={tab.label}
            className={cn(
              'app-tab-nav__item',
              isActive && 'app-tab-nav__item--active'
            )}
          >
            <Icon
              className={cn(
                'shrink-0',
                isRail ? 'size-[18px]' : 'size-4'
              )}
            />
            <span
              className={cn(
                'whitespace-nowrap font-medium',
                isRail ? 'text-[11px]' : 'text-[13px]'
              )}
            >
              {tab.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
}
