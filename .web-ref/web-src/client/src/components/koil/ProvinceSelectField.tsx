import { MapPinIcon } from 'lucide-react';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@client/src/components/ui/select';
import type { ProvinceInfo } from '@shared/api.interface';

interface ProvinceSelectFieldProps {
  value: string;
  onChange: (province: string) => void;
  provinces: ProvinceInfo[];
  size?: 'sm' | 'md';
  disabled?: boolean;
  className?: string;
}

export function ProvinceSelectField({
  value,
  onChange,
  provinces,
  size = 'md',
  disabled = false,
  className = '',
}: ProvinceSelectFieldProps) {
  const sizeClasses =
    size === 'sm'
      ? '!h-8 px-2.5 text-[11px]'
      : '!h-12 px-3.5 text-sm';

  return (
    <Select value={value} onValueChange={onChange} disabled={disabled || provinces.length === 0}>
      <SelectTrigger
        className={`min-w-0 shrink-0 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] hover:border-primary/40 focus:border-primary focus:ring-4 focus:ring-primary/15 data-[state=open]:[&_svg]:rotate-0 ${sizeClasses} ${className}`}
      >
        <div className="flex min-w-0 items-center gap-1.5">
          <MapPinIcon className={`shrink-0 text-primary ${size === 'sm' ? 'size-3' : 'size-4'}`} />
          <SelectValue />
        </div>
      </SelectTrigger>
      <SelectContent className="max-h-72">
        {provinces.map((p: ProvinceInfo) => (
          <SelectItem key={p.code} value={p.name}>
            {p.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
