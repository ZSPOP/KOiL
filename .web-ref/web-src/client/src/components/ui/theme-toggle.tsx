import { Monitor, Moon, Sun } from 'lucide-react';

import { Popover, PopoverContent, PopoverTrigger } from '@client/src/components/ui/popover';
import { useTheme, type ThemeMode } from '@client/src/hooks/useTheme';

const OPTIONS: { key: ThemeMode; label: string; icon: typeof Sun }[] = [
  { key: 'system', label: '跟随系统', icon: Monitor },
  { key: 'light', label: '浅色模式', icon: Sun },
  { key: 'dark', label: '深色模式', icon: Moon },
];

export function ThemeToggle() {
  const { theme, setTheme, resolvedTheme, isDark } = useTheme();

  // Button icon: show resolved theme icon; in system mode, show Monitor with a mini dot indicator
  const CurrentIcon = theme === 'system' ? Monitor : isDark ? Moon : Sun;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <button
          aria-label="切换主题"
          className="glass-soft group relative flex size-9 items-center justify-center rounded-full text-muted-foreground shadow-sm transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] sm:size-10"
        >
          <CurrentIcon className="size-[18px] transition-all duration-300" />
          {theme === 'system' && (
            <span
              className="absolute -bottom-0.5 -right-0.5 flex size-3 items-center justify-center rounded-full gradient-primary ring-2 ring-background"
              aria-hidden
            >
              <span className="size-1.5 rounded-full bg-white" />
            </span>
          )}
        </button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        sideOffset={8}
        className="w-44 rounded-2xl border-white/60 p-1.5 backdrop-blur-2xl"
        style={{
          background: 'var(--glass-bg-strong)',
          boxShadow: 'var(--glass-shadow)',
        }}
      >
        <div className="flex flex-col gap-0.5">
          {OPTIONS.map((opt) => {
            const Icon = opt.icon;
            const isActive = theme === opt.key;
            return (
              <button
                key={opt.key}
                onClick={() => setTheme(opt.key)}
                className={`group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all duration-200 ${
                  isActive
                    ? 'gradient-primary text-white shadow-md shadow-black/25'
                    : 'text-foreground-secondary'
                }`}
              >
                <Icon className={`size-4 ${isActive ? 'text-white' : 'text-muted-foreground'}`} />
                <span>{opt.label}</span>
              </button>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}

export default ThemeToggle;
