import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Trash2Icon, Fuel as FuelIcon, Check, CalendarIcon, XIcon, Edit2, Plus, GaugeIcon, DropletsIcon, WalletIcon, DollarSignIcon, CheckCircle2Icon, AlertTriangleIcon, AlertCircleIcon, Loader2Icon, HistoryIcon, InfoIcon, ChevronDownIcon, RouteIcon, MoreHorizontal } from 'lucide-react';
import { AnimatePresence, motion } from 'framer-motion';
import { format, isAfter, startOfDay } from 'date-fns';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { showConfirm } from '@lark-apaas/client-toolkit';

import { BounceScroll } from '@client/src/components/ui/bounce-scroll';
import { Button } from '@client/src/components/ui/button';
import { SwipeAction } from '@client/src/components/ui/swipe-action';
import { Input } from '@client/src/components/ui/input';
import { Label } from '@client/src/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@client/src/components/ui/dialog';
import { KoilCalendar } from '@client/src/components/koil/KoilCalendar';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@client/src/components/ui/select';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@client/src/components/ui/alert-dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@client/src/components/ui/dropdown-menu';

import { deleteKoilRecord, getKoilRecords, updateKoilRecord, createKoilRecord, getTodayOilPriceFromDb, getProvinces, getHistoryOilPrice, checkPriceDeviation, checkPriceDeviationBatch, precheckKoilRecord } from '@client/src/api/koil-records';
import type { KoilRecordWithDerived, KoilSummary, OilGrade, UpdateKoilRecordRequest, CreateKoilRecordRequest, OilPriceInfo, ProvinceInfo, LastKoilRecord, DuplicateCheckResponse, HistoryOilPriceResponse, MileageBoundaryResponse, KoilRecordPrecheckResponse } from '@shared/api.interface';
import { PaymentSummaryCard } from '@client/src/components/koil/PaymentSummaryCard';
import { ProvinceSelectField } from '@client/src/components/koil/ProvinceSelectField';
import { getTankCapacity } from '@client/src/utils/koil-settings';
import { getKoilItem } from '@client/src/utils/koil-storage';
import { useTranslation } from '@client/src/hooks/useTranslation';
import { useLocationPermission } from '@client/src/hooks/use-location-permission';

function fmt(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-';
  return value.toFixed(2);
}

interface RecordListProps {
  refreshKey: number;
  onDeleted: () => void;
  onEdited?: () => void;
  onSummaryUpdate: (summary: KoilSummary) => void;
  onItemsUpdate?: (items: KoilRecordWithDerived[]) => void;
  onTotalUpdate?: (total: number) => void;
  onInitialLoad: () => void;
  onGoToEntry?: () => void;
}

interface RecordCardProps {
  record: KoilRecordWithDerived;
  prevRecord?: KoilRecordWithDerived | null;
  onDelete: (id: string) => void;
  onEdit: (record: KoilRecordWithDerived) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  priceDeviation?: {
    hasDeviation: boolean;
    message: string | null;
    actualUnitPrice: number;
    referencePrice: number | null;
  } | null;
}

function formatDelta(
  current: number | null | undefined,
  prev: number | null | undefined,
  direction: 'lower-is-better' | 'higher-is-better',
  decimals: number = 2,
): { text: string; color: string } | null {
  if (current === null || current === undefined) return null;
  if (prev === null || prev === undefined) return null;
  if (prev === 0) return null;
  const diff = current - prev;
  const pct = (diff / prev) * 100;
  const arrow = diff >= 0 ? '↑' : '↓';
  const absDiff = Math.abs(diff);
  const absPct = Math.abs(pct);
  const text = `${arrow} ${absDiff.toFixed(decimals)} (${diff >= 0 ? '+' : ''}${absPct.toFixed(1)}%)`;
  let color = 'text-muted-foreground';
  if (diff === 0) {
    color = 'text-muted-foreground';
  } else if (direction === 'lower-is-better') {
    color = diff > 0 ? 'text-primary' : 'text-success';
  } else {
    color = diff > 0 ? 'text-primary' : 'text-success';
  }
  return { text, color };
}

function formatMd(dateStr: string): string {
  const d = new Date(dateStr);
  const mm = d.getMonth() + 1;
  const dd = d.getDate();
  return `${mm}月${dd}日`;
}

/* -------------------- 骨架屏组件 -------------------- */

function SkeletonShimmer() {
  return (
    <motion.div
      className="pointer-events-none absolute inset-0 overflow-hidden"
      initial={{ x: '-100%' }}
      animate={{ x: '100%' }}
      transition={{
        duration: 1.6,
        ease: 'linear',
        repeat: Infinity,
        repeatType: 'loop',
      }}
      aria-hidden="true"
    >
      <div className="h-full w-1/2 bg-gradient-to-r from-transparent via-[color:var(--shimmer-color,_rgba(255_255_255_0.60))] to-transparent" />
    </motion.div>
  );
}

function RecordCardSkeleton() {
  return (
    <div className="glass-soft relative overflow-hidden rounded-2xl p-4" aria-hidden="true">
      <SkeletonShimmer />
      {/* 顶部日期 + pill 占位 */}
      <div className="mb-3 flex flex-col gap-1">
        <div className="flex items-center gap-1.5">
          <div className="h-5 w-24 rounded-md bg-muted-foreground/15 sm:h-6 sm:w-28" />
          <div className="h-4 w-10 rounded-full bg-muted-foreground/10" />
          <div className="h-4 w-12 rounded-full bg-muted-foreground/10" />
        </div>
        <div className="h-3 w-44 rounded bg-muted-foreground/10" />
      </div>
      {/* 核心指标玻璃块占位 */}
      <div className="mb-3 glass-soft rounded-2xl p-3 sm:p-4">
        <div className="grid grid-cols-3 gap-1 sm:gap-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex flex-col items-center gap-1">
              <div className="h-3 w-14 rounded bg-muted-foreground/10" />
              <div className="h-6 w-12 rounded bg-muted-foreground/15 sm:h-7 sm:w-14" />
              <div className="h-2.5 w-16 rounded bg-muted-foreground/10" />
            </div>
          ))}
        </div>
      </div>
      {/* 底部三列占位 */}
      <div className="grid grid-cols-3 gap-2 border-t border-border/60 pt-3 sm:gap-4">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex flex-col items-center gap-0.5">
            <div className="h-2.5 w-10 rounded bg-muted-foreground/10" />
            <div className="h-4 w-14 rounded bg-muted-foreground/15" />
          </div>
        ))}
      </div>
    </div>
  );
}

function LoadingMoreSkeleton() {
  return (
    <div className="mx-auto flex w-52 flex-col items-center gap-2 sm:pr-14">
      <div className="glass-soft relative h-3 w-full overflow-hidden rounded-full" aria-hidden="true">
        <SkeletonShimmer />
      </div>
      <div className="glass-soft relative h-3 w-2/3 overflow-hidden rounded-full self-start" aria-hidden="true">
        <SkeletonShimmer />
      </div>
    </div>
  );
}

/* -------------------- 卡片组件 -------------------- */

function RecordCard({ record, prevRecord, onDelete, onEdit, open, onOpenChange, priceDeviation }: RecordCardProps) {
  const { t, language } = useTranslation();
  const hasDerived = record.distance !== null && record.distance !== undefined;
  const isAbnormal = record.isAbnormalOilConsumption === true;
  const level = record.oilConsumptionLevel;
  const isHigh = level === 'high';
  const isLow = level === 'low';

  const accentColorClass = isHigh ? 'text-destructive' : isLow ? 'text-warning' : 'text-primary';
  const accentBgClass = isHigh
    ? 'bg-destructive/5'
    : isLow
      ? 'bg-warning/5'
      : 'bg-surface-2/50';
  const accentRingClass = isHigh
    ? 'ring-1 ring-destructive/12'
    : isLow
      ? 'ring-1 ring-warning/15'
      : 'ring-1 ring-border';

  const cardContent = (
    <div className="group glass relative overflow-hidden rounded-2xl p-4 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]">
      {/* 左侧异常高亮竖条 */}
      {isAbnormal && (
        <div
          className={`absolute left-0 top-0 h-full w-1 ${isHigh ? 'bg-destructive' : 'bg-warning'}`}
          style={{
            background: isHigh
              ? 'linear-gradient(180deg, hsl(0, 65%, 63%) 0%, hsl(0, 60%, 55%) 100%)'
              : 'linear-gradient(180deg, hsl(38, 80%, 62%) 0%, hsl(38, 75%, 55%) 100%)',
          }}
        />
      )}

      {/* 顶部：日期行 + 操作按钮 */}
      <div className="mb-3 flex items-start justify-between gap-2">
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="text-base font-bold text-foreground sm:text-lg">
              {record.reoilDate}
            </span>
            {record.oilGrade && (
              <span className="inline-flex shrink-0 items-center rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary ring-1 ring-primary/20">
                {record.oilGrade}#
                {record.oilPrice !== null && record.oilPrice !== undefined && (
                  <span className="ml-1 tabular-nums opacity-80">¥{fmt(record.oilPrice)}</span>
                )}
              </span>
            )}
            {hasDerived ? (
              <span className="inline-flex shrink-0 items-center rounded-full bg-surface-2/80 px-2 py-0.5 text-[10px] font-medium text-foreground-secondary ring-1 ring-surface-2">
                {record.oilDays} {t('unit.day')}
              </span>
            ) : (
              <span className="inline-flex shrink-0 items-center rounded-full bg-muted-foreground/10 px-2 py-0.5 text-[10px] font-semibold text-muted-foreground ring-1 ring-muted-foreground/15">
                {t('record.initial')}
              </span>
            )}
            {isAbnormal && (
              <span
                className={`inline-flex shrink-0 items-center gap-0.5 rounded-full px-2 py-0.5 text-[10px] font-bold tracking-wide ${isHigh
                  ? 'bg-destructive/15 text-destructive ring-1 ring-destructive/30'
                  : 'bg-warning/15 text-warning ring-1 ring-warning/30'}`}
              >
                {isHigh ? t('record.highConsumption') : t('record.lowConsumption')}
              </span>
            )}
          </div>
          {hasDerived && record.prevReoilDate ? (
            <span className="text-[11px] text-muted-foreground">
              上次 {formatMd(record.prevReoilDate)} · 里程 {fmt(record.prevMileage)} → {fmt(record.mileage)} km
            </span>
          ) : (
            <span className="text-[11px] text-muted-foreground">
              当前里程 {fmt(record.mileage)} km
            </span>
          )}
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="-mr-2 -mt-1 size-11 shrink-0 text-muted-foreground"
              aria-label="更多操作"
              data-testid={`record-more-btn-${record.id}`}
            >
              <MoreHorizontal className="size-5" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-36">
            <DropdownMenuItem
              onClick={() => onEdit(record)}
              className="gap-2"
              data-testid={`record-more-edit-${record.id}`}
            >
              <Edit2 className="size-4" />
              <span>{t('common.edit')}</span>
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => onDelete(record.id)}
              className="gap-2 text-destructive focus:text-destructive"
              data-testid={`record-more-delete-${record.id}`}
            >
              <Trash2Icon className="size-4" />
              <span>{t('common.delete')}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* 核心指标三栏：平均油耗 · 每公里成本 · 行驶里程 */}
      {hasDerived ? (
        <div className={`mb-3 relative rounded-2xl p-3 sm:p-4 ${accentBgClass} ${accentRingClass}`}>
            <div className="grid grid-cols-3 gap-2 sm:gap-3">
            {[
              { label: t('metric.avgOilConsumption'), value: fmt(record.oilConsumption), unit: t('unit.lPer100km'), delta: formatDelta(record.oilConsumption, prevRecord?.oilConsumption, 'lower-is-better', 2) },
              { label: t('metric.costPerKm'), value: `¥${fmt(record.costPerKm)}`, unit: '', delta: formatDelta(record.costPerKm, prevRecord?.costPerKm, 'lower-is-better', 3) },
              { label: t('record.distance'), value: fmt(record.distance), unit: t('unit.km'), delta: formatDelta(record.distance, prevRecord?.distance, 'higher-is-better', 2) },
            ].map((item, idx) => (
              <div key={idx} className="flex min-w-0 flex-col gap-1 px-0.5">
                <span className={`truncate text-[10px] font-medium sm:text-[11px] ${isHigh
                  ? 'text-destructive/60'
                  : isLow
                    ? 'text-warning/60'
                    : 'text-primary/60'}`}>
                  {item.label}
                </span>
                <span className={`flex min-w-0 items-baseline gap-0.5 tabular-nums text-lg font-bold sm:text-xl ${accentColorClass}`}>
                  <span className="min-w-0 truncate">{item.value}</span>
                  {item.unit && (
                    <span className={`shrink-0 text-[10px] font-normal sm:text-xs ${isHigh
                      ? 'text-destructive/50'
                      : isLow
                        ? 'text-warning/50'
                        : 'text-primary/50'}`}>
                      {item.unit}
                    </span>
                  )}
                </span>
                {item.delta && (
                  <span className={`truncate text-[10px] tabular-nums font-medium sm:text-[11px] ${item.delta.color}`}>
                    {item.delta.text}
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
      ) : (
        <div className="mb-3 flex items-center gap-2 rounded-2xl border border-dashed border-muted-foreground/20 bg-surface-2/50 px-3 py-2.5 sm:px-4">
          <svg className="size-4 shrink-0 text-muted-foreground/60" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 16v-4M12 8h.01" />
          </svg>
          <span className="text-[11px] text-muted-foreground sm:text-xs">
            {t('record.initialDesc')}
          </span>
        </div>
      )}

      {/* 次要信息：加油量 · 实付金额 · 实付单价 */}
      <div className="grid grid-cols-3 gap-2 border-t border-border/60 py-3 text-center sm:gap-4 sm:py-4">
        <div className="flex flex-col gap-0.5">
          <span className="text-[10px] font-medium text-muted-foreground sm:text-[11px]">{t('form.oilAmount')}</span>
          <span className="tabular-nums text-sm font-semibold text-foreground sm:text-base">
            {fmt(record.oilAmount)} <span className="text-[10px] font-normal text-muted-foreground">{t('unit.liter')}</span>
          </span>
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-[10px] font-medium text-muted-foreground sm:text-[11px]">{t('form.actualAmount')}</span>
          <span className="tabular-nums text-sm font-semibold text-foreground sm:text-base">
            ¥{fmt(record.actualAmount)}
          </span>
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-[10px] font-medium text-muted-foreground sm:text-[11px]">{t('record.actualUnitPrice')}</span>
          <span className={`inline-flex items-center justify-center gap-1 tabular-nums text-sm font-semibold sm:text-base ${priceDeviation?.hasDeviation ? 'text-warning dark:text-amber-400' : 'text-foreground'}`}>
            ¥{record.unitPriceActual.toFixed(2)}
            {priceDeviation?.hasDeviation && (
              <AlertTriangleIcon
                className="size-3 shrink-0"
                aria-label={priceDeviation.message || '单价异常'}
              />
            )}
          </span>
        </div>
      </div>
    </div>
  );

  return (
    <div data-testid={`record-card-${record.id}`}>
      <SwipeAction
        open={open}
        onOpenChange={onOpenChange}
      actions={[
        {
          key: 'edit',
          label: t('common.edit'),
          icon: <Edit2 className="size-4" />,
          variant: 'primary',
          'data-testid': 'record-edit-action',
          onClick: () => {
            onOpenChange(false);
            onEdit(record);
          },
        },
        {
          key: 'delete',
          label: t('common.delete'),
          icon: <Trash2Icon className="size-4" />,
          variant: 'danger',
          onClick: () => {
            onOpenChange(false);
            setTimeout(() => onDelete(record.id), 200);
          },
        },
      ]}
    >
      {cardContent}
    </SwipeAction>
    </div>
  );
}

const PAGE_SIZE = 20;

function getBackfillDefaultGrade(): string {
  if (typeof window === 'undefined') return '92';
  try {
    const saved = getKoilItem('default-grade');
    if (saved === '92' || saved === '95' || saved === '98' || saved === '0') return saved;
  } catch {
    // ignore
  }
  return '92';
}

interface EditFormState {
  reoilDate: Date | null;
  mileage: string;
  oilAmount: string;
  actualAmount: string;
  payableAmount: string;
  oilGrade: string;
  oilPrice: string;
  province: string;
}

export function RecordList({
  refreshKey,
  onDeleted,
  onEdited,
  onSummaryUpdate,
  onItemsUpdate,
  onTotalUpdate,
  onInitialLoad,
  onGoToEntry,
}: RecordListProps) {
  const { t, language } = useTranslation();
  const [items, setItems] = useState<KoilRecordWithDerived[]>([]);
  const [priceDeviationMap, setPriceDeviationMap] = useState<Map<string, {
    hasDeviation: boolean;
    message: string | null;
    actualUnitPrice: number;
    referencePrice: number | null;
  }>>(new Map());
  const [totalCount, setTotalCount] = useState(0);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [editingRecord, setEditingRecord] = useState<KoilRecordWithDerived | null>(null);
  const [editForm, setEditForm] = useState<EditFormState>({
    reoilDate: null,
    mileage: '',
    oilAmount: '',
    actualAmount: '',
    payableAmount: '',
    oilGrade: '',
    oilPrice: '',
    province: '',
  });
  const [editErrors, setEditErrors] = useState<Record<string, string>>({});
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editDateOpen, setEditDateOpen] = useState(false);
  const editDateTriggerRef = useRef<HTMLButtonElement>(null);
  const [editCalPosition, setEditCalPosition] = useState<'below' | 'above'>('below');
  const [editCalStyle, setEditCalStyle] = useState<{ top: number; left: number; width: number } | null>(null);
  const editCalRef = useRef<HTMLDivElement>(null);
  const editFieldsRef = useRef<HTMLDivElement>(null);
  const [backfillOpen, setBackfillOpen] = useState(false);
  const [backfillDateOpen, setBackfillDateOpen] = useState(false);
  const backfillFieldsRef = useRef<HTMLDivElement>(null);
  const backfillDateTriggerRef = useRef<HTMLButtonElement>(null);
  const [backfillCalPosition, setBackfillCalPosition] = useState<'below' | 'above'>('below');
  const [backfillCalStyle, setBackfillCalStyle] = useState<{ top: number; left: number; width: number } | null>(null);
  const backfillCalRef = useRef<HTMLDivElement>(null);
  const [backfillForm, setBackfillForm] = useState({
    reoilDate: new Date(),
    mileage: '',
    oilAmount: '',
    actualAmount: '',
    payableAmount: '',
    oilGrade: getBackfillDefaultGrade() as OilGrade | '',
  });
  const [backfillErrors, setBackfillErrors] = useState<Record<string, string>>({});
  const [backfillSubmitting, setBackfillSubmitting] = useState(false);
  const [backfillSummaryExpanded, setBackfillSummaryExpanded] = useState(false);
  const [backfillPriceDeviation, setBackfillPriceDeviation] = useState<{
    hasDeviation: boolean;
    message: string | null;
  } | null>(null);
  const editDialogRef = useRef<HTMLDivElement>(null);
  const backfillDialogRef = useRef<HTMLDivElement>(null);

  // 点击日历外部关闭
  useEffect(() => {
    if (!editDateOpen) return;
    const handleClose = (e: Event) => {
      const target = e.target as Node;
      const inTrigger = editDateTriggerRef.current?.contains(target);
      const inCal = editCalRef.current?.contains(target);
      if (!inTrigger && !inCal) {
        setEditDateOpen(false);
      }
    };
    window.addEventListener('mousedown', handleClose);
    window.addEventListener('touchstart', handleClose, { passive: true });
    return () => {
      window.removeEventListener('mousedown', handleClose);
      window.removeEventListener('touchstart', handleClose);
    };
  }, [editDateOpen]);

  useEffect(() => {
    if (!editDateOpen || !editDateTriggerRef.current || !editFieldsRef.current || !editDialogRef.current) return;
    const trigger = editDateTriggerRef.current;
    const fields = editFieldsRef.current;
    const dialog = editDialogRef.current;
    const triggerRect = trigger.getBoundingClientRect();
    const fieldsRect = fields.getBoundingClientRect();
    const dialogRect = dialog.getBoundingClientRect();
    const width = fieldsRect.width;
    const estCalHeight = 340;
    const vh = window.innerHeight;
    const spaceBelow = vh - triggerRect.bottom - 16;
    const spaceAbove = triggerRect.top - 16;
    const position: 'below' | 'above' = spaceBelow >= estCalHeight
      ? 'below'
      : spaceAbove >= estCalHeight
        ? 'above'
        : spaceBelow >= spaceAbove ? 'below' : 'above';
    setEditCalPosition(position);
    setEditCalStyle({
      top: position === 'below'
        ? triggerRect.bottom - dialogRect.top + 8
        : triggerRect.top - dialogRect.top - 8 - estCalHeight,
      left: fieldsRect.left - dialogRect.left,
      width,
    });
  }, [editDateOpen]);

  useEffect(() => {
    if (!backfillDateOpen) return;
    const handleClose = (e: Event) => {
      const target = e.target as Node;
      const inTrigger = backfillDateTriggerRef.current?.contains(target);
      const inCal = backfillCalRef.current?.contains(target);
      if (!inTrigger && !inCal) {
        setBackfillDateOpen(false);
      }
    };
    window.addEventListener('mousedown', handleClose);
    window.addEventListener('touchstart', handleClose, { passive: true });
    return () => {
      window.removeEventListener('mousedown', handleClose);
      window.removeEventListener('touchstart', handleClose);
    };
  }, [backfillDateOpen]);

  useEffect(() => {
    if (!backfillDateOpen || !backfillDateTriggerRef.current || !backfillFieldsRef.current || !backfillDialogRef.current) return;
    const trigger = backfillDateTriggerRef.current;
    const fields = backfillFieldsRef.current;
    const dialog = backfillDialogRef.current;
    const triggerRect = trigger.getBoundingClientRect();
    const fieldsRect = fields.getBoundingClientRect();
    const dialogRect = dialog.getBoundingClientRect();
    const width = fieldsRect.width;
    const estCalHeight = 340;
    const vh = window.innerHeight;
    const spaceBelow = vh - triggerRect.bottom - 16;
    const spaceAbove = triggerRect.top - 16;
    const position: 'below' | 'above' = spaceBelow >= estCalHeight
      ? 'below'
      : spaceAbove >= estCalHeight
        ? 'above'
        : spaceBelow >= spaceAbove ? 'below' : 'above';
    setBackfillCalPosition(position);
    setBackfillCalStyle({
      top: position === 'below'
        ? triggerRect.bottom - dialogRect.top + 8
        : triggerRect.top - dialogRect.top - 8 - estCalHeight,
      left: fieldsRect.left - dialogRect.left,
      width,
    });
  }, [backfillDateOpen]);

  const [backfillFocusedField, setBackfillFocusedField] = useState<string | null>(null);
  const [backfillOilPriceData, setBackfillOilPriceData] = useState<OilPriceInfo[]>([]);
  const [backfillOilPriceLoading, setBackfillOilPriceLoading] = useState(false);
  const [backfillProvinces, setBackfillProvinces] = useState<ProvinceInfo[]>([]);
  const [backfillProvince, setBackfillProvince] = useState('');
  const [backfillProvinceSource, setBackfillProvinceSource] = useState<string>('');
  const { state: locationState } = useLocationPermission();
  const [historyPriceResult, setHistoryPriceResult] = useState<HistoryOilPriceResponse | null>(null);
  const [historyPriceLoading, setHistoryPriceLoading] = useState(false);
  const historyPriceDedupeRef = useRef<string>('');
  const backfillSubmitLockRef = useRef(false);
  const [backfillPrevRecord, setBackfillPrevRecord] = useState<LastKoilRecord | null>(null);
  const [backfillPrevLoading, setBackfillPrevLoading] = useState(false);
  const [duplicateResult, setDuplicateResult] = useState<DuplicateCheckResponse | null>(null);
  const [duplicateChecking, setDuplicateChecking] = useState(false);
  const [mileageConflictMsg, setMileageConflictMsg] = useState<string | null>(null);
  const [backfillBoundary, setBackfillBoundary] = useState<MileageBoundaryResponse | null>(null);
  const backfillPrecheckTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const backfillPrecheckSeqRef = useRef<number>(0);
  const backfillPrecheckFetchedRef = useRef<string>('');
  const backfillSubmitConfirmedRef = useRef<boolean>(false);
  const [backfillDirty, setBackfillDirty] = useState(false);
  const backfillCloseGuardRef = useRef(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [initialLoaded, setInitialLoaded] = useState(false);
  const [activeMonth, setActiveMonth] = useState<string>('');
  const [openCardId, setOpenCardId] = useState<string | null>(null);
  const isFetchingRef = useRef(false);
  const listContainerRef = useRef<HTMLDivElement>(null);
  const groupRefs = useRef<Map<string, HTMLDivElement>>(new Map());
  const sentinelRef = useRef<HTMLDivElement>(null);

  const gradeOptions = useMemo<{ value: OilGrade; label: string }[]>(() => [
    { value: '92', label: t('grade.92') },
    { value: '95', label: t('grade.95') },
    { value: '98', label: t('grade.98') },
    { value: '0', label: t('grade.diesel') },
  ], [t]);

  // 补录统一预检：日期/里程变化时 300ms 防抖，一次请求返回 boundary + duplicate + conflict + prevRecord
  useEffect(() => {
    const date = backfillForm.reoilDate;
    if (!date || !(date instanceof Date) || Number.isNaN(date.getTime())) {
      if (backfillPrecheckTimerRef.current) {
        clearTimeout(backfillPrecheckTimerRef.current);
        backfillPrecheckTimerRef.current = null;
      }
      setBackfillPrevRecord(null);
      setBackfillBoundary(null);
      setDuplicateResult(null);
      setMileageConflictMsg(null);
      return;
    }
    const dateStr = format(date, 'yyyy-MM-dd');
    const mileageVal = backfillForm.mileage;
    const mileageNum = Number(mileageVal);
    const hasMileage = mileageVal !== '' && !Number.isNaN(mileageNum) && mileageNum > 0;
    const cacheKey = `${dateStr}|${hasMileage ? mileageNum : ''}`;

    if (backfillPrecheckTimerRef.current) {
      clearTimeout(backfillPrecheckTimerRef.current);
    }
    backfillPrecheckTimerRef.current = setTimeout(() => {
      if (backfillPrecheckFetchedRef.current === cacheKey) return;
      const seq = backfillPrecheckSeqRef.current + 1;
      backfillPrecheckSeqRef.current = seq;
      backfillPrecheckFetchedRef.current = cacheKey;
      setBackfillPrevLoading(true);
      setDuplicateChecking(true);

      precheckKoilRecord({
        reoilDate: dateStr,
        ...(hasMileage ? { mileage: mileageNum } : {}),
        includeBoundary: true,
        includePrevRecord: true,
      })
        .then((data: KoilRecordPrecheckResponse) => {
          if (backfillPrecheckSeqRef.current !== seq) return;
          setBackfillPrevRecord(data.prevRecord);
          setBackfillBoundary(data.boundary);
          if (hasMileage) {
            setDuplicateResult(data.duplicate);
            setMileageConflictMsg(data.mileageChain.hasConflict ? data.mileageChain.message : null);
          }
        })
        .catch(() => {
          if (backfillPrecheckSeqRef.current !== seq) return;
          setBackfillPrevRecord(null);
          setBackfillBoundary(null);
          setDuplicateResult(null);
          setMileageConflictMsg(null);
        })
        .finally(() => {
          if (backfillPrecheckSeqRef.current !== seq) return;
          setBackfillPrevLoading(false);
          setDuplicateChecking(false);
        });
    }, 300);

    return () => {
      if (backfillPrecheckTimerRef.current) {
        clearTimeout(backfillPrecheckTimerRef.current);
        backfillPrecheckTimerRef.current = null;
      }
    };
  }, [backfillForm.reoilDate, backfillForm.mileage]);

  // 加载省份列表（组件 mount 时拉一次）
  useEffect(() => {
    const fetchProvinces = async (): Promise<void> => {
      try {
        const list: ProvinceInfo[] = await getProvinces();
        setBackfillProvinces(list);
      } catch (error) {
        logger.error('补录弹窗获取省份列表失败', error);
      }
    };
    void fetchProvinces();
  }, []);

  // 补录弹窗打开时：若未手动选择过，同步定位链路的省份作为默认值
  useEffect(() => {
    if (!backfillOpen) return;
    if (backfillProvinceSource === '手动选择') return;
    if (locationState.province && locationState.province !== backfillProvince) {
      setBackfillProvince(locationState.province);
      setBackfillProvinceSource(locationState.provinceSource || '默认省份');
    }
  }, [backfillOpen]);

  // 省份变化时刷新实时油价
  useEffect(() => {
    if (!backfillProvince) return;
    const fetchOilPrice = async (): Promise<void> => {
      try {
        setBackfillOilPriceLoading(true);
        const res = await getTodayOilPriceFromDb(backfillProvince);
        if (res.success && res.data && res.data.length > 0) {
          setBackfillOilPriceData(res.data);
        }
      } catch (error) {
        logger.error('补录弹窗获取油价失败', error);
      } finally {
        setBackfillOilPriceLoading(false);
      }
    };
    void fetchOilPrice();
  }, [backfillProvince]);

  // 监听设置页偏好变化：仅当前来源为默认省份（含兜底）时跟随；GPS/手动/IP 不受影响
  // 同时同步默认油号（油号未手动改过才跟随）
  useEffect(() => {
    const onPreferencesChanged = (): void => {
      if (
        backfillProvinceSource === '默认省份' ||
        backfillProvinceSource === '默认省份（兜底）'
      ) {
        const defaultProvince = locationState.province;
        if (defaultProvince && defaultProvince !== backfillProvince) {
          setBackfillProvince(defaultProvince);
          setBackfillProvinceSource(
            locationState.provinceSource || '默认省份',
          );
        }
      }
      if (!backfillDirty) {
        const defaultGrade = getBackfillDefaultGrade() as OilGrade | '';
        if (defaultGrade && defaultGrade !== backfillForm.oilGrade) {
          setBackfillForm((prev) => ({ ...prev, oilGrade: defaultGrade }));
        }
      }
    };
    window.addEventListener('koil-preferences-changed', onPreferencesChanged);
    return () => {
      window.removeEventListener('koil-preferences-changed', onPreferencesChanged);
    };
  }, [backfillProvinceSource, backfillProvince, locationState.province, locationState.provinceSource]);

  const handleBackfillProvinceChange = (province: string): void => {
    setBackfillProvince(province);
    setBackfillProvinceSource('手动选择');
  };

  // 当前选中油号对应的油价
  const backfillCurrentOilPrice: number | null = useMemo(() => {
    const found = backfillOilPriceData.find(
      (item: OilPriceInfo) => item.grade === backfillForm.oilGrade,
    );
    return found ? found.price : null;
  }, [backfillOilPriceData, backfillForm.oilGrade]);

  const backfillMileageNum = Number(backfillForm.mileage);
  const backfillFuelAmountNum = Number(backfillForm.oilAmount);
  const backfillActualAmountNum = Number(backfillForm.actualAmount);

  const isBackfillMileageBackwards = Boolean(
    backfillBoundary?.prevMileage != null &&
      backfillForm.mileage !== '' &&
      !Number.isNaN(backfillMileageNum) &&
      backfillMileageNum > 0 &&
      backfillMileageNum <= backfillBoundary.prevMileage,
  );

  const isBackfillFuelAbnormal = backfillFuelAmountNum > 0 && backfillFuelAmountNum > getTankCapacity();
  const isBackfillActualAbnormal = backfillActualAmountNum > 0 && backfillActualAmountNum > 1500;

  // 编辑 ratio 校验（独立计算值，用于 PaymentSummaryCard）
  const { isEditRatioInvalid, editRatioErrorMessage } = useMemo(() => {
    const fuelNum = Number(editForm.oilAmount);
    const actualNum = Number(editForm.actualAmount);
    const payableNum = Number(editForm.payableAmount);
    const oilPriceNum = Number(editForm.oilPrice);
    let payableForCheck: number | null = null;
    if (payableNum > 0) {
      payableForCheck = payableNum;
    } else if (oilPriceNum > 0 && fuelNum > 0) {
      payableForCheck = fuelNum * oilPriceNum;
    }
    if (payableForCheck != null && payableForCheck > 0 && actualNum > 0) {
      const ratio = actualNum / payableForCheck;
      if (ratio > 1.01) {
        return { isEditRatioInvalid: true, editRatioErrorMessage: '实付金额高于应付金额，请确认' };
      }
      if (ratio < 0.75) {
        return { isEditRatioInvalid: true, editRatioErrorMessage: '实付金额与应付金额差异过大（优惠超过25%），请确认' };
      }
    }
    return { isEditRatioInvalid: false, editRatioErrorMessage: '' };
  }, [editForm.oilAmount, editForm.actualAmount, editForm.payableAmount, editForm.oilPrice]);

  const backfillWarningCount = [
    isBackfillMileageBackwards,
    isBackfillFuelAbnormal,
    isBackfillActualAbnormal,
  ].filter(Boolean).length;

  const markBackfillDirty = (): void => {
    if (!backfillDirty) setBackfillDirty(true);
  };

  const resetBackfillForm = (): void => {
    setBackfillForm({
      reoilDate: new Date(),
      mileage: '',
      oilAmount: '',
      actualAmount: '',
      payableAmount: '',
      oilGrade: getBackfillDefaultGrade() as OilGrade | '',
    });
    setBackfillErrors({});
    setBackfillDirty(false);
    setDuplicateResult(null);
    setMileageConflictMsg(null);
    setBackfillDateOpen(false);
    setBackfillBoundary(null);
    setBackfillPrevRecord(null);
    setHistoryPriceResult(null);
    backfillSubmitConfirmedRef.current = false;
    backfillPrecheckFetchedRef.current = '';
  };

  const handleBackfillClose = (): void => {
    if (backfillDirty && !backfillSubmitting && !backfillCloseGuardRef.current) {
      backfillCloseGuardRef.current = true;
      showConfirm(t('form.unsavedConfirm'))
        .then((confirmed: unknown) => {
          if (confirmed === false || confirmed === 0 || confirmed === '') {
            requestAnimationFrame(() => {
              backfillCloseGuardRef.current = false;
            });
            return;
          }
          backfillCloseGuardRef.current = false;
          setBackfillOpen(false);
          resetBackfillForm();
        })
        .catch(() => {
          requestAnimationFrame(() => {
            backfillCloseGuardRef.current = false;
          });
        });
      return;
    }
    backfillCloseGuardRef.current = false;
    setBackfillOpen(false);
    resetBackfillForm();
  };

  const isBackfillDateToday = useMemo(() => {
    const date = backfillForm.reoilDate;
    if (!date || !(date instanceof Date) || Number.isNaN(date.getTime())) return true;
    return format(date, 'yyyy-MM-dd') === format(new Date(), 'yyyy-MM-dd');
  }, [backfillForm.reoilDate]);

  const backfillDisplayOilPrice: number | null = useMemo(() => {
    if (!isBackfillDateToday && historyPriceResult?.price) {
      return historyPriceResult.price;
    }
    return backfillCurrentOilPrice;
  }, [isBackfillDateToday, historyPriceResult, backfillCurrentOilPrice]);

  const backfillCalculatedPayable: string = useMemo(() => {
    const price = backfillDisplayOilPrice;
    if (price === null || price === undefined) return '';
    const fuelNum = Number(backfillForm.oilAmount);
    if (Number.isNaN(fuelNum) || fuelNum <= 0) return '';
    return (fuelNum * price).toFixed(2);
  }, [backfillDisplayOilPrice, backfillForm.oilAmount]);

  // 补录 ratio 校验（独立计算值，用于 PaymentSummaryCard）
  const { isBackfillRatioInvalid, backfillRatioErrorMessage } = useMemo(() => {
    const actualNum = backfillActualAmountNum;
    const payableFromForm = Number(backfillForm.payableAmount);
    let payableForCheck: number | null = null;
    if (payableFromForm > 0) {
      payableForCheck = payableFromForm;
    } else if (backfillCalculatedPayable !== '' && !Number.isNaN(Number(backfillCalculatedPayable))) {
      payableForCheck = Number(backfillCalculatedPayable);
    }
    if (payableForCheck != null && payableForCheck > 0 && actualNum > 0) {
      const ratio = actualNum / payableForCheck;
      if (ratio > 1.01) {
        return { isBackfillRatioInvalid: true, backfillRatioErrorMessage: '实付金额高于应付金额，请确认' };
      }
      if (ratio < 0.75) {
        return { isBackfillRatioInvalid: true, backfillRatioErrorMessage: '实付金额与应付金额差异过大（优惠超过25%），请确认' };
      }
    }
    return { isBackfillRatioInvalid: false, backfillRatioErrorMessage: '' };
  }, [backfillActualAmountNum, backfillForm.payableAmount, backfillCalculatedPayable]);

  useEffect(() => {
    if (isBackfillDateToday) {
      setHistoryPriceResult(null);
      return;
    }
    const date = backfillForm.reoilDate;
    const grade = backfillForm.oilGrade;
    const province = backfillProvince;
    if (!date || !(date instanceof Date) || Number.isNaN(date.getTime()) || !grade || !province) {
      setHistoryPriceResult(null);
      return;
    }
    const dateStr = format(date, 'yyyy-MM-dd');
    const requestKey = `${province}_${grade}_${dateStr}`;

    if (historyPriceDedupeRef.current === requestKey) {
      return;
    }

    historyPriceDedupeRef.current = requestKey;
    setHistoryPriceLoading(true);

    let cancelled = false;
    getHistoryOilPrice(province, grade, dateStr)
      .then((data) => {
        if (cancelled) return;
        if (historyPriceDedupeRef.current !== requestKey) return;
        setHistoryPriceResult(data);
      })
      .catch(() => {
        if (cancelled) return;
        if (historyPriceDedupeRef.current !== requestKey) return;
        setHistoryPriceResult(null);
      })
      .finally(() => {
        if (cancelled) return;
        if (historyPriceDedupeRef.current !== requestKey) return;
        setHistoryPriceLoading(false);
      });

    return () => {
      cancelled = true;
      if (historyPriceDedupeRef.current === requestKey) {
        historyPriceDedupeRef.current = '';
      }
    };
  }, [backfillForm.reoilDate, backfillForm.oilGrade, backfillProvince, isBackfillDateToday]);

  const backfillDeviationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    const province = backfillProvince;
    const grade = backfillForm.oilGrade;
    const date = backfillForm.reoilDate;
    const oilAmt = Number(backfillForm.oilAmount);
    const actualAmt = Number(backfillForm.actualAmount);

    if (
      !province ||
      !grade ||
      !date ||
      !(date instanceof Date) ||
      Number.isNaN(date.getTime()) ||
      Number.isNaN(oilAmt) || oilAmt <= 0 ||
      Number.isNaN(actualAmt) || actualAmt <= 0
    ) {
      setBackfillPriceDeviation(null);
      return;
    }

    const dateStr = format(date, 'yyyy-MM-dd');
    if (backfillDeviationTimerRef.current) {
      clearTimeout(backfillDeviationTimerRef.current);
    }
    backfillDeviationTimerRef.current = setTimeout(() => {
      checkPriceDeviation({
        province,
        grade: grade as OilGrade,
        date: dateStr,
        oilAmount: oilAmt,
        actualAmount: actualAmt,
      }).then((result) => {
        setBackfillPriceDeviation({
          hasDeviation: result.hasDeviation,
          message: result.message,
        });
      }).catch(() => {
        setBackfillPriceDeviation(null);
      });
    }, 400);

    return () => {
      if (backfillDeviationTimerRef.current) {
        clearTimeout(backfillDeviationTimerRef.current);
      }
    };
  }, [backfillProvince, backfillForm.oilGrade, backfillForm.reoilDate, backfillForm.oilAmount, backfillForm.actualAmount]);

  const loadMore = useCallback(async () => {
    if (isFetchingRef.current) return;
    if (!hasMore) return;
    isFetchingRef.current = true;
    setLoadingMore(true);

    try {
      const lastItem = items[items.length - 1];
      const params = {
        pageSize: PAGE_SIZE,
        ...(lastItem
          ? { cursorDate: lastItem.reoilDate, cursorId: lastItem.id, cursorMileage: String(lastItem.mileage) }
          : {}),
      };

      const data = await getKoilRecords(params);

      if (lastItem) {
        setItems((prev) => [...prev, ...data.items]);
      } else {
        setItems(data.items);
      }

      setHasMore(data.hasMore);
      setTotalCount(data.totalCount);
      if (onTotalUpdate) onTotalUpdate(data.totalCount);
      onSummaryUpdate(data.summary);
      if (onItemsUpdate) {
        const newItems = lastItem ? [...items, ...data.items] : data.items;
        onItemsUpdate(newItems);
      }
    } catch (error) {
      logger.error('加载更多加油记录失败', error);
      toast.error(t('record.loadFailed'));
    } finally {
      setLoadingMore(false);
      setInitialLoaded(true);
      onInitialLoad();
      isFetchingRef.current = false;
    }
  }, [items, hasMore, onSummaryUpdate, onItemsUpdate, onTotalUpdate, onInitialLoad]);

  useEffect(() => {
    const candidates = items.filter((item: KoilRecordWithDerived) =>
      item.province &&
      item.oilGrade &&
      item.oilAmount > 0 &&
      item.actualAmount > 0 &&
      !priceDeviationMap.has(item.id),
    );
    if (candidates.length === 0) return;

    const batchItems = candidates.map((item: KoilRecordWithDerived) => ({
      id: item.id,
      province: item.province as string,
      grade: (item.oilGrade as OilGrade) || '92',
      date: item.reoilDate,
      oilAmount: Number(item.oilAmount),
      actualAmount: Number(item.actualAmount),
    }));

    checkPriceDeviationBatch(batchItems)
      .then((response) => {
        setPriceDeviationMap((prev) => {
          const next = new Map(prev);
          for (const r of response.items) {
            next.set(r.id, {
              hasDeviation: r.result.hasDeviation,
              message: r.result.message,
              actualUnitPrice: r.result.actualUnitPrice,
              referencePrice: r.result.referencePrice,
            });
          }
          return next;
        });
      })
      .catch((error) => {
        logger.error('批量单价偏差检测失败', error);
      });
  }, [items, priceDeviationMap]);

  useEffect(() => {
    if (items.length === 0 && !initialLoaded && !isFetchingRef.current) {
      void loadMore();
    }
  }, [items.length, initialLoaded, loadMore]);

  useEffect(() => {
    if (!initialLoaded || !hasMore || isFetchingRef.current) return;
    if (items.length < totalCount && items.length < PAGE_SIZE) {
      void loadMore();
    }
  }, [initialLoaded, hasMore, items.length, totalCount, loadMore]);

  useEffect(() => {
    setItems([]);
    setTotalCount(0);
    setHasMore(true);
    setInitialLoaded(false);
    isFetchingRef.current = false;
  }, [refreshKey]);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!(sentinel instanceof Element)) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingMore && items.length > 0) {
          void loadMore();
        }
      },
      { rootMargin: '120px', threshold: 0.1 },
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, loadingMore, items.length, loadMore]);

  useEffect(() => {
    if (!openCardId) return;
    const handleClickOutside = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest('[data-swipe-open="true"]')) {
        setOpenCardId(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [openCardId]);

  // 按月份分组
  const monthGroups = useMemo(() => {
    const groups = new Map<string, KoilRecordWithDerived[]>();
    for (const record of items) {
      const monthKey = record.reoilDate.slice(0, 7); // YYYY-MM
      const group = groups.get(monthKey);
      if (group) {
        group.push(record);
      } else {
        groups.set(monthKey, [record]);
      }
    }
    return Array.from(groups.entries());
  }, [items]);

  const earliestRecord = useMemo(() => {
    if (items.length === 0) return null;
    let earliest = items[0];
    for (const rec of items) {
      if (rec.reoilDate < earliest.reoilDate ||
          (rec.reoilDate === earliest.reoilDate && rec.mileage < earliest.mileage)) {
        earliest = rec;
      }
    }
    return earliest;
  }, [items]);

  const backfillMileageRange = useMemo<{
    lower: number | null;
    upper: number | null;
    lowerLabel: string | null;
    upperLabel: string | null;
    hint: string | null;
    sameDayMileages: number[];
    isBeforeFirst: boolean;
  }>(() => {
    if (!backfillBoundary) {
      return {
        lower: null, upper: null, lowerLabel: null, upperLabel: null, hint: null,
        sameDayMileages: [], isBeforeFirst: false,
      };
    }
    const lower = backfillBoundary.prevMileage;
    const upper = backfillBoundary.nextMileage;
    const lowerLabel = backfillBoundary.prevDate
      ? `${backfillBoundary.prevDate} ${Math.round(lower ?? 0)}km`
      : null;
    const upperLabel = backfillBoundary.nextDate
      ? `${backfillBoundary.nextDate} ${Math.round(upper ?? 0)}km`
      : null;
    let hint: string | null = null;
    if (lower !== null && upper !== null) {
      hint = `该日期可输入里程：${Math.round(lower)} ~ ${Math.round(upper)} km`;
    } else if (lower !== null) {
      hint = `该日期可输入里程：大于 ${Math.round(lower)} km`;
    } else if (upper !== null) {
      hint = `该日期可输入里程：小于 ${Math.round(upper)} km`;
    }
    return {
      lower, upper, lowerLabel, upperLabel, hint,
      sameDayMileages: backfillBoundary.sameDayMileages ?? [],
      isBeforeFirst: backfillBoundary.isBeforeFirst ?? false,
    };
  }, [backfillBoundary]);

  const isBackfillMileageOutOfRange = useMemo(() => {
    const mileageNum = Number(backfillForm.mileage);
    if (!backfillForm.mileage || Number.isNaN(mileageNum) || mileageNum <= 0) return false;
    if (backfillMileageRange.lower !== null && mileageNum <= backfillMileageRange.lower) return true;
    if (backfillMileageRange.upper !== null && mileageNum >= backfillMileageRange.upper) return true;
    return false;
  }, [backfillForm.mileage, backfillMileageRange]);

  const backfillPreviewDistance = useMemo<number | null>(() => {
    const mileageNum = Number(backfillForm.mileage);
    if (!backfillBoundary || !backfillBoundary.prevMileage || !backfillForm.mileage || Number.isNaN(mileageNum) || mileageNum <= 0) return null;
    const dist = mileageNum - backfillBoundary.prevMileage;
    return dist > 0 ? dist : null;
  }, [backfillForm.mileage, backfillBoundary]);

  const backfillPreviewFuelConsumption = useMemo<number | null>(() => {
    if (!backfillPreviewDistance || backfillPreviewDistance <= 0) return null;
    const fuelNum = Number(backfillForm.oilAmount);
    if (!fuelNum || Number.isNaN(fuelNum) || fuelNum <= 0) return null;
    return (fuelNum / backfillPreviewDistance) * 100;
  }, [backfillForm.oilAmount, backfillPreviewDistance]);

  // 月份索引列表（倒序，最新在前）
  const monthIndexList = useMemo(
    () => monthGroups.map(([monthKey]) => monthKey),
    [monthGroups],
  );

  const formatMonthLabel = (monthKey: string): string => {
    const [year, month] = monthKey.split('-');
    return `${year}年${parseInt(month, 10)}月`;
  };

  const formatMonthShort = (monthKey: string): string => {
    const month = monthKey.slice(5, 7);
    return `${parseInt(month, 10)}月`;
  };

  const scrollToMonth = (monthKey: string) => {
    const el = groupRefs.current.get(monthKey);
    if (el) {
      setActiveMonth(monthKey);
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  const setGroupRef = (monthKey: string, el: HTMLDivElement | null) => {
    if (el) {
      groupRefs.current.set(monthKey, el);
    } else {
      groupRefs.current.delete(monthKey);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteId) return;
    try {
      setDeleting(true);
      await deleteKoilRecord(deleteId);
      toast.success(t('record.deleteSuccess'));
      setDeleteId(null);
      onDeleted();
    } catch (error) {
      logger.error('删除加油记录失败', error);
      toast.error(t('record.deleteFailed'));
    } finally {
      setDeleting(false);
    }
  };

  const handleOpenEdit = (record: KoilRecordWithDerived) => {
    setEditingRecord(record);
    setEditForm({
      reoilDate: new Date(record.reoilDate + 'T00:00:00'),
      mileage: String(record.mileage),
      oilAmount: String(record.oilAmount),
      actualAmount: String(record.actualAmount),
      payableAmount: String(record.payableAmount),
      oilGrade: record.oilGrade ?? '',
      oilPrice: record.oilPrice !== null ? String(record.oilPrice) : '',
      province: record.province ?? locationState.province ?? '',
    });
    setEditErrors({});
  };

  const validateEditForm = (): boolean => {
    const errors: Record<string, string> = {};
    if (!editForm.reoilDate) {
      errors.reoilDate = t('val.dateRequired');
    } else if (format(editForm.reoilDate, 'yyyy-MM-dd') > format(new Date(), 'yyyy-MM-dd')) {
      errors.reoilDate = '不能补录未来日期的记录';
    }
    const mileageNum = Number(editForm.mileage);
    if (!editForm.mileage || Number.isNaN(mileageNum) || mileageNum <= 0) {
      errors.mileage = t('val.mileageMustPositive');
    }
    const oilAmountNum = Number(editForm.oilAmount);
    if (!editForm.oilAmount || Number.isNaN(oilAmountNum) || oilAmountNum <= 0) {
      errors.oilAmount = t('val.oilAmountMustPositive');
    } else if (oilAmountNum < 1) {
      errors.oilAmount = t('val.oilAmountTooSmall');
    } else if (oilAmountNum > getTankCapacity()) {
      errors.oilAmount = t('form.oilAmountTooLarge', { max: getTankCapacity() });
    }
    const actualAmountNum = Number(editForm.actualAmount);
    if (!editForm.actualAmount || Number.isNaN(actualAmountNum) || actualAmountNum <= 0) {
      errors.actualAmount = t('val.actualAmountMustPositive');
    } else if (actualAmountNum > 1500) {
      errors.actualAmount = '实付金额超过1500元，请确认是否正确';
    }
    const payableAmountNum = Number(editForm.payableAmount);
    if (editForm.payableAmount !== '' && (Number.isNaN(payableAmountNum) || payableAmountNum <= 0)) {
      errors.payableAmount = t('val.payableMustPositive');
    }
    let payableForCheck: number | null = null;
    if (payableAmountNum > 0) {
      payableForCheck = payableAmountNum;
    } else if (editForm.oilPrice && !Number.isNaN(Number(editForm.oilPrice)) && Number(editForm.oilPrice) > 0 && oilAmountNum > 0) {
      payableForCheck = oilAmountNum * Number(editForm.oilPrice);
    }
    if (payableForCheck != null && payableForCheck > 0 && actualAmountNum > 0 && !errors.actualAmount) {
      const ratio = actualAmountNum / payableForCheck;
      if (ratio > 1.01) {
        errors.actualAmount = '实付金额高于应付金额，请确认';
      } else if (ratio < 0.75) {
        errors.actualAmount = '实付金额与应付金额差异过大（优惠超过25%），请确认';
      }
    }
    setEditErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmitEdit = async () => {
    if (!editingRecord) return;
    if (!validateEditForm()) return;
    try {
      setEditSubmitting(true);
      const dateStr = editForm.reoilDate
        ? format(editForm.reoilDate, 'yyyy-MM-dd')
        : '';
      const mileageNum = Number(editForm.mileage);
      const oilAmountNum = Number(editForm.oilAmount);
      const actualAmountNum = Number(editForm.actualAmount);

      if (dateStr && !Number.isNaN(mileageNum) && mileageNum > 0) {
        const precheckResult = await precheckKoilRecord({
          reoilDate: dateStr,
          mileage: mileageNum,
          oilAmount: !Number.isNaN(oilAmountNum) && oilAmountNum > 0 ? oilAmountNum : undefined,
          actualAmount: !Number.isNaN(actualAmountNum) && actualAmountNum > 0 ? actualAmountNum : undefined,
          excludeId: editingRecord.id,
          includeBoundary: false,
          includePrevRecord: false,
        });
        if (precheckResult.isBeforeFirst) {
          toast.error('不能早于你的第一条加油记录');
          setEditSubmitting(false);
          return;
        }
        if (precheckResult.mileageChain.hasConflict) {
          toast.error(precheckResult.mileageChain.message || '里程冲突，无法保存');
          setEditSubmitting(false);
          return;
        }
        if (precheckResult.duplicate.exists) {
          toast.error(t('form.duplicateRecord'));
          setEditSubmitting(false);
          return;
        }
      }

      const data: UpdateKoilRecordRequest = {
        reoilDate: editForm.reoilDate
          ? format(editForm.reoilDate, 'yyyy-MM-dd')
          : undefined,
        mileage: Number(editForm.mileage),
        oilAmount: Number(editForm.oilAmount),
        actualAmount: Number(editForm.actualAmount),
        payableAmount:
          editForm.payableAmount !== '' && !Number.isNaN(Number(editForm.payableAmount))
            ? Number(editForm.payableAmount)
            : Number(editForm.actualAmount),
        oilGrade: (editForm.oilGrade as OilGrade) || null,
        oilPrice: editForm.oilPrice ? Number(editForm.oilPrice) : null,
        province: editForm.province || null,
      };
      await updateKoilRecord(editingRecord.id, data);
      toast.success(t('record.editSuccess'));
      setEditingRecord(null);
      if (onEdited) onEdited();
    } catch (error) {
      logger.error('编辑加油记录失败', error);
      const msg =
        (error as { response?: { data?: { error?: { message?: string } } } })
          ?.response?.data?.error?.message || t('record.editFailed');
      toast.error(msg);
    } finally {
      setEditSubmitting(false);
    }
  };

  const handleBackfillClear = (field: 'mileage' | 'oilAmount' | 'actualAmount' | 'payableAmount') => {
    markBackfillDirty();
    // 应付金额自动计算模式下，点击 X 清空加油量（计算依据）
    if (field === 'payableAmount' && backfillCalculatedPayable !== '') {
      setBackfillForm((prev) => ({ ...prev, oilAmount: '', payableAmount: '' }));
      setBackfillErrors((prev) => {
        const next = { ...prev };
        delete next.oilAmount;
        delete next.payableAmount;
        return next;
      });
      return;
    }
    setBackfillForm((prev) => ({ ...prev, [field]: '' }));
    setBackfillErrors((prev) => {
      const next = { ...prev };
      delete next[field];
      return next;
    });
  };

  const validateBackfillForm = (): boolean => {
    const errors: Record<string, string> = {};
    if (!backfillForm.reoilDate) {
      errors.reoilDate = t('val.dateRequired');
    } else if (format(backfillForm.reoilDate, 'yyyy-MM-dd') > format(new Date(), 'yyyy-MM-dd')) {
      errors.reoilDate = '不能补录未来日期的记录';
    } else if (earliestRecord) {
      const backfillDateStr = format(backfillForm.reoilDate, 'yyyy-MM-dd');
      const mileageNum = Number(backfillForm.mileage);
      if (
        backfillDateStr < earliestRecord.reoilDate ||
        (backfillDateStr === earliestRecord.reoilDate && !Number.isNaN(mileageNum) && mileageNum < earliestRecord.mileage)
      ) {
        errors.reoilDate = `补录不能早于你的第一条记录（${earliestRecord.reoilDate} ${earliestRecord.mileage}km）`;
      }
    }
    const mileageNum = Number(backfillForm.mileage);
    if (!backfillForm.mileage || Number.isNaN(mileageNum) || mileageNum <= 0) {
      errors.mileage = t('val.mileageMustPositive');
    } else if (backfillMileageRange.lower !== null && mileageNum <= backfillMileageRange.lower) {
      errors.mileage = `里程超出合法区间：需大于 ${Math.round(backfillMileageRange.lower)} km（${backfillMileageRange.lowerLabel ?? '上一日记录'}）`;
    } else if (backfillMileageRange.upper !== null && mileageNum >= backfillMileageRange.upper) {
      errors.mileage = `里程超出合法区间：需小于 ${Math.round(backfillMileageRange.upper)} km（${backfillMileageRange.upperLabel ?? '下一日记录'}）`;
    }
    const oilAmountNum = Number(backfillForm.oilAmount);
    if (!backfillForm.oilAmount || Number.isNaN(oilAmountNum) || oilAmountNum <= 0) {
      errors.oilAmount = t('val.oilAmountMustPositive');
    } else if (oilAmountNum < 1) {
      errors.oilAmount = t('val.oilAmountTooSmall');
    } else if (oilAmountNum > getTankCapacity()) {
      errors.oilAmount = t('form.oilAmountTooLarge', { max: getTankCapacity() });
    }
    const actualAmountNum = Number(backfillForm.actualAmount);
    if (!backfillForm.actualAmount || Number.isNaN(actualAmountNum) || actualAmountNum <= 0) {
      errors.actualAmount = t('val.actualAmountMustPositive');
    } else if (actualAmountNum > 1500) {
      errors.actualAmount = '实付金额超过1500元，请确认是否正确';
    }
    const payableAmountNum = Number(backfillForm.payableAmount);
    if (backfillForm.payableAmount !== '' && (Number.isNaN(payableAmountNum) || payableAmountNum <= 0)) {
      errors.payableAmount = t('val.payableMustPositive');
    }
    let payableForCheck: number | null = null;
    if (payableAmountNum > 0) {
      payableForCheck = payableAmountNum;
    } else if (backfillCalculatedPayable !== '' && !Number.isNaN(Number(backfillCalculatedPayable))) {
      payableForCheck = Number(backfillCalculatedPayable);
    }
    if (payableForCheck != null && payableForCheck > 0 && actualAmountNum > 0) {
      const ratio = actualAmountNum / payableForCheck;
      if (ratio > 1.01) {
        errors.actualAmount = '实付金额高于应付金额，请确认';
      } else if (ratio < 0.75) {
        errors.actualAmount = '实付金额与应付金额差异过大（优惠超过25%），请确认';
      }
    }
    setBackfillErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmitBackfill = async () => {
    if (backfillSubmitLockRef.current) return;
    if (!validateBackfillForm()) return;
    const dateStr = format(backfillForm.reoilDate, 'yyyy-MM-dd');
    const mileageNum = Number(backfillForm.mileage);
    const oilAmountNum = Number(backfillForm.oilAmount);
    const actualAmountNum = Number(backfillForm.actualAmount);
    try {
      backfillSubmitLockRef.current = true;
      setBackfillSubmitting(true);
      const precheckResult = await precheckKoilRecord({
        reoilDate: dateStr,
        mileage: mileageNum,
        oilAmount: oilAmountNum,
        actualAmount: actualAmountNum,
        source: 'backfill_modal',
        includeBoundary: false,
        includePrevRecord: false,
      });
      if (precheckResult.isBeforeFirst) {
        toast.error('不能早于你的第一条加油记录');
        return;
      }
      if (precheckResult.mileageChain.hasConflict) {
        toast.error(precheckResult.mileageChain.message || '里程冲突，无法补录');
        return;
      }
      if (precheckResult.duplicate.exists) {
        toast.error(t('form.duplicateRecord'));
        return;
      }
      if (precheckResult.duplicate.similarExists) {
        setDuplicateResult(precheckResult.duplicate);
      }
      const data: CreateKoilRecordRequest = {
        reoilDate: dateStr,
        mileage: mileageNum,
        oilAmount: oilAmountNum,
        actualAmount: actualAmountNum,
        payableAmount:
          backfillCalculatedPayable !== ''
            ? Number(backfillCalculatedPayable)
            : backfillForm.payableAmount !== '' && !Number.isNaN(Number(backfillForm.payableAmount))
              ? Number(backfillForm.payableAmount)
              : actualAmountNum,
        oilGrade: (backfillForm.oilGrade as OilGrade) || null,
        oilPrice: backfillCurrentOilPrice,
        province: backfillProvince || null,
        source: 'backfill_modal',
      };
      await createKoilRecord(data);
      toast.success(t('backfill.success'));
      setBackfillOpen(false);
      resetBackfillForm();
      if (onEdited) onEdited();
    } catch (error) {
      logger.error('补录加油记录失败', error);
      const msg =
        (error as { response?: { data?: { error?: { message?: string } } } })
          ?.response?.data?.error?.message || t('backfill.failed');
      toast.error(msg);
    } finally {
      setBackfillSubmitting(false);
      backfillSubmitLockRef.current = false;
    }
  };

  return (
    <>
      <div className="glass relative rounded-2xl p-4 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]">
        {/* 标题区 */}
        <div className="mb-5 flex items-center justify-between sm:mb-6">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-2xl gradient-primary shadow-sm">
              <svg className="size-5 text-white" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
              </svg>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-lg font-bold text-foreground sm:text-xl">
                {t('record.title')}
              </span>
              <span className="text-xs text-muted-foreground">
                {t('record.scrollToLoad')}
              </span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {initialLoaded && totalCount > 0 && (
              <span className="glass-soft rounded-full px-3 py-1.5 text-[11px] font-semibold text-primary sm:text-xs">
                {t('record.totalCount', { count: totalCount })}
              </span>
            )}
            <Button
              type="button"
              size="sm"
              onClick={() => setBackfillOpen(true)}
               className="h-8 gap-1 rounded-full gradient-primary px-3 text-[11px] font-semibold text-white shadow-sm shadow-black/20 transition-all duration-200 ease-[cubic-bezier(0.2,0.8,0.2,1)] sm:h-9 sm:gap-1.5 sm:px-4 sm:text-xs"
            >
              <Plus className="size-3.5 sm:size-4" />
              {t('backfill.button')}
            </Button>
          </div>
        </div>

        {items.length === 0 && !initialLoaded ? (
          <div className="flex flex-col gap-4 py-2">
            {[0, 1, 2].map((i) => (
              <RecordCardSkeleton key={i} />
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="glass-soft flex flex-col items-center gap-5 rounded-3xl py-14">
            <div className="glass flex size-24 items-center justify-center rounded-3xl">
              <FuelIcon className="size-11 text-primary/60" strokeWidth={1.5} />
            </div>
            <div className="flex flex-col items-center gap-1.5 px-6 text-center">
              <span className="text-lg font-bold text-foreground">
                {t('empty.noRecordsList')}
              </span>
              <span className="text-sm text-muted-foreground">
                {t('empty.noRecordsListDesc')}
              </span>
            </div>
            {onGoToEntry && (
              <Button
                size="sm"
                onClick={onGoToEntry}
                className="mt-1 rounded-full px-6 py-5 text-sm font-semibold"
              >
                {t('empty.goAdd')}
              </Button>
            )}
          </div>
        ) : (
          <div ref={listContainerRef} className="relative flex flex-col gap-4">
            {/* 右侧月份快速索引（md+ 显示） */}
            {monthIndexList.length > 1 && (
              <div className="absolute right-0 top-0 z-10 hidden md:block">
                <div className="glass-soft flex flex-col items-center gap-1 rounded-full px-2 py-3">
                  {monthIndexList.map((monthKey) => (
                    <button
                      key={monthKey}
                      type="button"
                      onClick={() => scrollToMonth(monthKey)}
                      className={`flex h-9 w-10 items-center justify-center rounded-full text-center text-[11px] font-medium transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] ${activeMonth === monthKey
                        ? 'gradient-primary text-white shadow-sm'
                         : 'text-muted-foreground'}`}
                       aria-label={`跳转到${formatMonthLabel(monthKey)}`}
                    >
                      {formatMonthShort(monthKey)}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 分组记录列表 */}
            <div className="pr-0 md:pr-16">
              <AnimatePresence mode="popLayout">
                {monthGroups.map(([monthKey, records]) => (
                  <motion.div
                    key={monthKey}
                    ref={(el) => setGroupRef(monthKey, el)}
                    layout
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0, height: 0 }}
                    transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}
                    className="mb-5 last:mb-0"
                  >
                    {/* 月份标题 sticky */}
                    <div className="sticky top-0 z-[5] mb-3 -mx-1 px-1 pb-2 pt-1">
                      <div className="glass-soft flex items-center gap-2.5 rounded-full px-4 py-2">
                        <span className="block size-3 rounded-sm gradient-primary" />
                        <span className="text-sm font-bold text-foreground">
                          {formatMonthLabel(monthKey)}
                        </span>
                        <span className="text-[11px] font-medium text-muted-foreground">
                          {t('record.itemCount', { count: records.length })}
                        </span>
                      </div>
                    </div>

                    {/* 组内记录卡片 */}
                    <div className="flex flex-col gap-4">
                      {records.map((record, idx) => {
                        // 在同组内找下一个（更旧的）记录；若已是组末，则在整个 items 列表里找
                        let prev: KoilRecordWithDerived | null = null;
                        if (idx < records.length - 1) {
                          prev = records[idx + 1];
                        } else {
                          const globalIdx = items.findIndex((r) => r.id === record.id);
                          if (globalIdx >= 0 && globalIdx < items.length - 1) {
                            prev = items[globalIdx + 1];
                          }
                        }
                        return (
                          <motion.div
                            key={record.id}
                            layout
                            initial={{ opacity: 0, y: -12 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, height: 0, marginBottom: 0, y: -8 }}
                            transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}
                          >
                            <RecordCard
                              record={record}
                              prevRecord={prev}
                              onDelete={setDeleteId}
                              onEdit={handleOpenEdit}
                              open={openCardId === record.id}
                              onOpenChange={(open) => setOpenCardId(open ? record.id : null)}
                              priceDeviation={priceDeviationMap.get(record.id) || null}
                            />
                          </motion.div>
                        );
                      })}
                    </div>
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>

            {/* 加载更多 / 已全部加载 */}
            <div ref={sentinelRef} className="py-6 text-center md:pr-16">
              {loadingMore && <LoadingMoreSkeleton />}
              {!hasMore && items.length > 0 && !loadingMore && (
                <div className="glass-soft inline-flex items-center gap-2 rounded-full px-5 py-2 text-xs font-medium text-muted-foreground">
                  <span className="flex size-5 items-center justify-center rounded-full gradient-primary text-white">
                    <Check className="size-3" strokeWidth={3} />
                  </span>
                  {t('record.allShown', { count: items.length })}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      <AlertDialog
        open={deleteId !== null}
        onOpenChange={(open) => {
          if (!open && !deleting) setDeleteId(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('record.deleteConfirm')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('record.deleteConfirmDesc')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault();
                void handleConfirmDelete();
              }}
              disabled={deleting}
              className="bg-destructive text-destructive-foreground"
            >
              {deleting ? t('common.deleting') : t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 编辑对话框 */}
      <Dialog
        open={editingRecord !== null}
        onOpenChange={(open) => {
          if (!open && !editSubmitting) setEditingRecord(null);
        }}
      >
        <DialogContent
          showCloseButton={false}
              className="max-w-md p-0 max-h-[90vh] flex flex-col overflow-hidden"
         >
          <div ref={editDialogRef} className="relative flex flex-col min-h-0 flex-1">
            <div className="px-6 pb-2 pt-6 sm:px-7 sm:pt-7">
            <DialogHeader className="text-left">
              <DialogTitle className="text-xl font-bold text-foreground">
                {t('record.editTitle')}
              </DialogTitle>
            </DialogHeader>
            <button
              type="button"
              onClick={() => {
                if (!editSubmitting) setEditingRecord(null);
              }}
              className="absolute right-5 top-5 flex size-8 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200"
              aria-label={t('common.close')}
            >
              <XIcon className="size-4" />
             </button>
           </div>

            <BounceScroll className="flex-1 min-h-0 space-y-4 px-6 py-4 sm:px-7">
              <div ref={editFieldsRef} className="space-y-4">
             {/* 省份选择 */}
             <div className="flex flex-col gap-2">
               <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                 加油省份
               </Label>
               <ProvinceSelectField
                 value={editForm.province}
                 onChange={(val) => setEditForm((prev) => ({ ...prev, province: val }))}
                 provinces={backfillProvinces}
                 size="md"
                 className="w-full"
               />
             </div>

             {/* 加油日期 + 油号（2 列并排） */}
             <div className="grid grid-cols-2 gap-4">
               <div className="flex flex-col gap-2">
                 <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                   <CalendarIcon className="size-3.5 text-primary" />
                   {t('form.reoilDate')}
                 </Label>
                 <button
                   type="button"
                   onClick={(e) => {
                     e.stopPropagation();
                     setEditDateOpen((o) => !o);
                   }}
                   className="flex h-12 w-full items-center gap-2 rounded-2xl border border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-left text-sm font-normal tabular-nums text-foreground shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15"
                   aria-label={t('form.reoilDate')}
                   aria-expanded={editDateOpen}
                   data-testid="edit-date-trigger"
                   ref={editDateTriggerRef}
                 >
                   {editForm.reoilDate
                     ? format(editForm.reoilDate, 'yyyy-MM-dd')
                     : '请选择日期'}
                  </button>
                </div>

                <div className="flex flex-col gap-2">
                 <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                   <FuelIcon className="size-3.5 text-primary" />
                   {t('form.oilGrade')}
                 </Label>
                 <Select
                   value={editForm.oilGrade}
                   onValueChange={(val) =>
                     setEditForm((prev) => ({ ...prev, oilGrade: val }))
                   }
                 >
                    <SelectTrigger className="!h-12 !w-full shrink-0 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {gradeOptions.map((opt) => (
                        <SelectItem key={opt.value} value={opt.value}>
                          {opt.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
               </div>

               {editErrors.reoilDate && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{editErrors.reoilDate}</span>
                 </div>
               )}

              <div className="grid grid-cols-2 gap-4">
               {/* 本次里程 */}
               <div className="flex flex-col gap-2">
                 <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                   <GaugeIcon className="size-3.5 text-primary" />
                   {t('form.mileageKm')}
                 </Label>
                 <Input
                   type="number"
                   inputMode="decimal"
                   step="0.01"
                   value={editForm.mileage}
                   onChange={(e) =>
                     setEditForm((prev) => ({ ...prev, mileage: e.target.value }))
                   }
                   className="h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15"
                 />
                 {editErrors.mileage && (
                   <span className="text-[11px] text-destructive">
                     {editErrors.mileage}
                   </span>
                 )}
               </div>
               {/* 加油量 */}
               <div className="flex flex-col gap-2">
                 <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                   <DropletsIcon className="size-3.5 text-primary" />
                   {t('form.oilAmountL')}
                 </Label>
                 <Input
                   type="number"
                   inputMode="decimal"
                   step="0.01"
                   value={editForm.oilAmount}
                   onChange={(e) =>
                     setEditForm((prev) => ({ ...prev, oilAmount: e.target.value }))
                   }
                   className="h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15"
                 />
                 {editErrors.oilAmount && (
                   <span className="text-[11px] text-destructive">
                     {editErrors.oilAmount}
                   </span>
                 )}
                  </div>
                 </div>

                {(editErrors.mileage || editErrors.oilAmount) && (
                <div className="space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200" style={{ animationFillMode: 'backwards' }}>
                  {editErrors.mileage && (
                    <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                      <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                      <span className="leading-snug">{editErrors.mileage}</span>
                    </div>
                  )}
                  {editErrors.oilAmount && (
                    <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                      <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                      <span className="leading-snug">{editErrors.oilAmount}</span>
                    </div>
                  )}
                </div>
                )}

                {/* 应付金额 + 实付金额（2 列并排） */}
               <div className="grid grid-cols-2 gap-4">
                 <div className="flex flex-col gap-2">
                   <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                     <DollarSignIcon className="size-3.5 text-primary" />
                     {t('form.payableAmount')}
                   </Label>
                   <Input
                     type="number"
                     inputMode="decimal"
                     step="0.01"
                     value={editForm.payableAmount}
                     onChange={(e) =>
                       setEditForm((prev) => ({ ...prev, payableAmount: e.target.value }))
                     }
                     placeholder={t('record.sameAsActual')}
                     className="h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15"
                   />
                      {editErrors.payableAmount && (
                        <span className="sr-only text-[11px] text-destructive">
                          {editErrors.payableAmount}
                        </span>
                      )}
                    </div>

                    <div className="flex flex-col gap-2">
                      <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                        <WalletIcon className="size-3.5 text-primary" />
                        {t('form.actualAmount')}
                      </Label>
                      <Input
                        type="number"
                        inputMode="decimal"
                        step="0.01"
                        value={editForm.actualAmount}
                        onChange={(e) =>
                          setEditForm((prev) => ({ ...prev, actualAmount: e.target.value }))
                        }
                        className="h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15"
                      />
                      {editErrors.actualAmount && (
                        <span className="sr-only text-[11px] text-destructive">
                          {editErrors.actualAmount}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* 实付金额综合信息块 */}
                  <PaymentSummaryCard
                    oilAmount={Number(editForm.oilAmount)}
                    payableAmount={
                      editForm.payableAmount !== '' && !Number.isNaN(Number(editForm.payableAmount)) && Number(editForm.payableAmount) > 0
                        ? Number(editForm.payableAmount)
                        : editForm.oilPrice && !Number.isNaN(Number(editForm.oilPrice)) && Number(editForm.oilPrice) > 0 && Number(editForm.oilAmount) > 0
                          ? Number(editForm.oilAmount) * Number(editForm.oilPrice)
                          : null
                    }
                    actualAmount={Number(editForm.actualAmount)}
                    referencePrice={null}
                    referenceNote="编辑态 · 无历史参考价对比"
                    isError={isEditRatioInvalid}
                    errorMessage={editRatioErrorMessage || null}
                  />

                  {(editErrors.payableAmount || editErrors.actualAmount) && (
                  <div className="space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200" style={{ animationFillMode: 'backwards' }}>
                    {editErrors.payableAmount && (
                      <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                        <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                        <span className="leading-snug">{editErrors.payableAmount}</span>
                      </div>
                    )}
                    {editErrors.actualAmount && (
                      <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                        <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                        <span className="leading-snug">{editErrors.actualAmount}</span>
                      </div>
                    )}
                  </div>
                  )}

               {/* 油价（可选）*/}
               <div className="flex flex-col gap-2">
                 <Label className="text-xs font-medium text-muted-foreground sm:text-sm">
                   {t('record.officialPrice')}
                 </Label>
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    value={editForm.oilPrice}
                    onChange={(e) =>
                      setEditForm((prev) => ({ ...prev, oilPrice: e.target.value }))
                    }
                    placeholder={t('record.optional')}
                    className="h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15"
                  />
                </div>
                 </div>
            </BounceScroll>

           {editDateOpen && editCalStyle && (
            <div
              className="absolute z-50"
              ref={editCalRef}
              style={{
                top: editCalStyle.top,
                left: editCalStyle.left,
                width: editCalStyle.width,
              }}
            >
              <KoilCalendar
                value={editForm.reoilDate ? format(editForm.reoilDate, 'yyyy-MM-dd') : ''}
                onChange={(dateStr) => {
                  const [y, m, d] = dateStr.split('-').map(Number);
                  setEditForm((prev) => ({ ...prev, reoilDate: new Date(y, m - 1, d) }));
                  setEditDateOpen(false);
                }}
                max={format(new Date(), 'yyyy-MM-dd')}
                locale={language}
                variant="glass"
              />
            </div>
          )}

          <DialogFooter className="flex-row gap-2 px-6 pb-6 pt-2 sm:px-7 sm:pb-7">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                if (!editSubmitting) setEditingRecord(null);
              }}
              disabled={editSubmitting}
               className="flex-1 rounded-full border-border-light bg-[var(--glass-bg-strong)] text-foreground-secondary"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="button"
              onClick={() => void handleSubmitEdit()}
              disabled={editSubmitting}
               className="flex-1 rounded-full gradient-primary text-white shadow-lg shadow-black/25"
            >
              {editSubmitting ? t('common.saving') : t('record.saveEdit')}
            </Button>
          </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>

         {/* 补录对话框 */}
        <Dialog
          open={backfillOpen}
          onOpenChange={(open) => {
            if (!open) {
              if (backfillCloseGuardRef.current) return;
              handleBackfillClose();
            }
          }}
        >
         <DialogContent
           showCloseButton={false}
           onInteractOutside={(e) => {
             if (backfillCloseGuardRef.current) {
               e.preventDefault();
             }
           }}
             className="max-w-md p-0 max-h-[90vh] flex flex-col overflow-hidden"
           >
              <div ref={backfillDialogRef} className="relative flex flex-col min-h-0 flex-1">
               <div className="px-6 pb-2 pt-6 sm:px-7 sm:pt-7">
            <DialogHeader className="text-left">
              <DialogTitle className="text-xl font-bold text-foreground">
                {t('backfill.dialogTitle')}
              </DialogTitle>
              <p className="mt-1 text-xs text-muted-foreground sm:text-sm">
                {t('backfill.dialogDesc')}
              </p>
            </DialogHeader>
            <button
              type="button"
              onClick={handleBackfillClose}
              className="absolute right-5 top-5 flex size-8 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200"
              aria-label={t('common.close')}
            >
              <XIcon className="size-4" />
            </button>
          </div>

           <BounceScroll className="flex-1 min-h-0 space-y-4 px-6 py-4 sm:px-7">
              <div ref={backfillFieldsRef} className="space-y-4">
              {/* 省份选择 */}
              <div className="flex flex-col gap-2">
                <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                  加油省份
                </Label>
                <ProvinceSelectField
                  value={backfillProvince}
                  onChange={handleBackfillProvinceChange}
                  provinces={backfillProvinces}
                  size="md"
                  className="w-full"
                />
              </div>

             {/* 日期 + 油号（2 列并排） */}
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-2">
                   <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                     <CalendarIcon className="size-3.5 text-primary" />
                     {t('form.reoilDate')}
                   </Label>
                   <button
                     type="button"
                     onClick={(e) => {
                       e.stopPropagation();
                       setBackfillDateOpen((o) => !o);
                     }}
                     className="flex h-12 w-full items-center gap-2 rounded-2xl border border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-left text-sm font-normal tabular-nums text-foreground shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:outline-none focus:ring-4 focus:ring-primary/15"
                     aria-label={t('form.reoilDate')}
                     aria-expanded={backfillDateOpen}
                     data-testid="backfill-date-trigger"
                     ref={backfillDateTriggerRef}
                   >
                     {format(backfillForm.reoilDate, 'yyyy-MM-dd')}
                   </button>
                  {backfillErrors.reoilDate && (
                   <span className="text-[11px] text-destructive">
                     {backfillErrors.reoilDate}
                   </span>
                 )}
               </div>

              <div className="flex flex-col gap-2">
                <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                  <FuelIcon className="size-3.5 text-primary" />
                  {t('form.oilGrade')}
                  {backfillDisplayOilPrice !== null && (
                    <span className="tabular-nums text-xs font-medium text-primary/80 sm:text-sm">
                      ¥{backfillDisplayOilPrice.toFixed(2)}/{t('unit.liter')}
                    </span>
                  )}
                </Label>
                 <Select
                   value={backfillForm.oilGrade}
                   onValueChange={(val) => {
                     setBackfillForm((prev) => ({ ...prev, oilGrade: val as OilGrade }));
                     markBackfillDirty();
                   }}
                 >
                   <SelectTrigger className="!h-12 !w-full shrink-0 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15">
                     <SelectValue />
                   </SelectTrigger>
                   <SelectContent>
                     {gradeOptions.map((opt) => (
                       <SelectItem key={opt.value} value={opt.value}>
                         {opt.label}
                       </SelectItem>
                     ))}
                   </SelectContent>
                 </Select>
               </div>
             </div>

             {/* 里程区间提示 + 同日记录提示 */}
              {backfillMileageRange.hint && !backfillErrors.reoilDate && (
                <div className="flex flex-col gap-1.5 animate-in fade-in slide-in-from-top-1 duration-200">
                  <div className="flex items-center gap-1.5 rounded-full border border-primary/20 bg-primary/10 px-3 py-1.5 text-[11px] font-medium text-primary transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] dark:border-primary/25 dark:bg-primary/15 sm:text-xs">
                    <InfoIcon className="size-3.5 shrink-0" />
                    <span className="truncate tabular-nums">{backfillMileageRange.hint}</span>
                  </div>
                  {isBackfillDateToday && backfillMileageRange.lower !== null && backfillMileageRange.upper === null && (
                    <div className="flex items-start gap-1.5 text-[11px] text-muted-foreground pl-1">
                      <span className="leading-snug">今天已是最新一天，里程需大于上次加油</span>
                    </div>
                  )}
                  {backfillMileageRange.sameDayMileages.length > 0 && (
                    <div className="flex items-center gap-1.5 rounded-full border border-muted-foreground/15 bg-muted-foreground/10 px-3 py-1 text-[11px] font-medium text-muted-foreground transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] dark:border-muted-foreground/20 dark:bg-muted-foreground/15 sm:text-xs">
                      <span className="shrink-0">当天已有</span>
                      <span className="truncate tabular-nums">
                        {backfillMileageRange.sameDayMileages.map((m: number) => `${Math.round(m)} km`).join('、')}
                      </span>
                    </div>
                  )}
                </div>
              )}

             {/* 里程 + 加油量（2 列并排） */}
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                  <GaugeIcon className="size-3.5 text-primary" />
                  {t('form.mileageKm')}
                </Label>
                <div className="relative">
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    value={backfillForm.mileage}
                    onChange={(e) => {
                       setBackfillForm((prev) => ({ ...prev, mileage: e.target.value }));
                       markBackfillDirty();
                     }}
                     onFocus={() => setBackfillFocusedField('mileage')}
                    onBlur={() => setBackfillFocusedField((prev) => (prev === 'mileage' ? null : prev))}
                    onKeyDown={(e) => {
                       if (e.nativeEvent.isComposing) return;
                       if (e.key === 'Enter') e.preventDefault();
                     }}
                     placeholder="0.00"
                      className={`h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-base font-semibold tabular-nums shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${backfillErrors.mileage ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                    />
                    {backfillFocusedField === 'mileage' && backfillForm.mileage !== '' && (
                     <button
                       type="button"
                       aria-label="清除"
                       onMouseDown={(e) => e.preventDefault()}
                       onClick={() => handleBackfillClear('mileage')}
                       className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                     >
                       <XIcon className="size-4" />
                     </button>
                   )}
                    {backfillFocusedField !== 'mileage' && backfillForm.mileage !== '' && !Number.isNaN(Number(backfillForm.mileage)) && Number(backfillForm.mileage) > 0 && !backfillErrors.mileage && !isBackfillMileageBackwards && !mileageConflictMsg && !duplicateResult?.exists && (
                     <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                   )}
                 </div>
                </div>

                <div className="flex flex-col gap-2">
                 <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                   <DropletsIcon className="size-3.5 text-primary" />
                   {t('form.oilAmountL')}
                </Label>
                <div className="relative">
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    value={backfillForm.oilAmount}
                    onChange={(e) => {
                       setBackfillForm((prev) => ({ ...prev, oilAmount: e.target.value }));
                       markBackfillDirty();
                     }}
                     onFocus={() => setBackfillFocusedField('oilAmount')}
                    onBlur={() => setBackfillFocusedField((prev) => (prev === 'oilAmount' ? null : prev))}
                    onKeyDown={(e) => {
                       if (e.nativeEvent.isComposing) return;
                       if (e.key === 'Enter') e.preventDefault();
                     }}
                      placeholder="0.00"
                       className={`h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-base font-semibold tabular-nums shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${backfillErrors.oilAmount ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                   />
                   {backfillFocusedField === 'oilAmount' && backfillForm.oilAmount !== '' && (
                    <button
                      type="button"
                      aria-label="清除"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => handleBackfillClear('oilAmount')}
                       className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                    >
                      <XIcon className="size-4" />
                    </button>
                  )}
                   {backfillFocusedField !== 'oilAmount' && backfillForm.oilAmount !== '' && !Number.isNaN(Number(backfillForm.oilAmount)) && Number(backfillForm.oilAmount) > 0 && !backfillErrors.oilAmount && !isBackfillFuelAbnormal && (
                     <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                   )}
                 </div>
                </div>
              </div>

              {/* 行驶预览条 */}
              {backfillPreviewDistance !== null && !isBackfillMileageBackwards && !isBackfillMileageOutOfRange && (
                <div className="flex flex-wrap items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/15 px-2.5 py-1.5 text-xs font-medium text-emerald-600 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] animate-in fade-in slide-in-from-top-1 duration-300 dark:border-emerald-400/25 dark:bg-emerald-500/20 dark:text-emerald-400 sm:px-3 sm:text-sm">
                  <RouteIcon className="size-4 shrink-0" />
                  <span className="truncate tabular-nums">
                    上次加油 → 本次 · 行驶 {Math.round(backfillPreviewDistance)} km
                    {backfillPreviewFuelConsumption !== null && ` · 预计油耗 ${backfillPreviewFuelConsumption.toFixed(1)} L/100km`}
                  </span>
                </div>
              )}

              {/* 里程 + 加油量 通栏提示 */}
              {(backfillBoundary?.isBeforeFirst || mileageConflictMsg || duplicateResult?.exists || (duplicateResult && !duplicateResult.exists && duplicateResult.sameDayCount > 0) || isBackfillMileageBackwards || isBackfillMileageOutOfRange || isBackfillFuelAbnormal || duplicateChecking || backfillErrors.mileage || backfillErrors.oilAmount) && (
              <div className="mt-1.5 flex flex-col space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200" style={{ animationFillMode: 'backwards' }}>
                {backfillBoundary?.isBeforeFirst ? (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">补录不能早于你的第一条加油记录</span>
                  </div>
                ) : mileageConflictMsg ? (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{mileageConflictMsg}</span>
                  </div>
                ) : duplicateResult?.exists ? (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{t('form.duplicateSameDay')}</span>
                  </div>
                ) : duplicateResult && !duplicateResult.exists && duplicateResult.similarExists ? (
                  <div className="flex items-start gap-2 rounded-xl bg-amber-500/10 px-3 py-2 text-[12px] text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">同日有一条相似记录（加油量/金额接近），请注意核对</span>
                  </div>
                ) : duplicateResult && !duplicateResult.exists && duplicateResult.sameDayCount > 0 ? (
                  <div className="flex items-start gap-2 rounded-xl bg-amber-500/10 px-3 py-2 text-[12px] text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{t('form.sameDayCount', { count: duplicateResult.sameDayCount })}</span>
                  </div>
                ) : null}
                {isBackfillMileageBackwards && (
                  <div className="flex items-start gap-2 rounded-xl bg-amber-500/10 px-3 py-2 text-[12px] text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{t('form.mileageBackwardWarn')}</span>
                  </div>
                )}
                {isBackfillMileageOutOfRange && backfillMileageRange.hint && (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">里程超出合法区间 — {backfillMileageRange.hint}</span>
                  </div>
                )}
                {isBackfillFuelAbnormal && (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{t('form.oilAmountTooLarge', { max: getTankCapacity() })}</span>
                  </div>
                )}
                {duplicateChecking && (
                  <div className="flex items-center gap-2 rounded-xl bg-muted/10 px-3 py-2 text-[12px] text-muted-foreground dark:bg-muted/15">
                    <Loader2Icon className="size-3.5 shrink-0 animate-spin" />
                    <span className="leading-snug">{t('form.validating')}</span>
                  </div>
                )}
                {backfillErrors.mileage && (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{backfillErrors.mileage}</span>
                  </div>
                )}
                {backfillErrors.oilAmount && (
                  <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                    <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                    <span className="leading-snug">{backfillErrors.oilAmount}</span>
                  </div>
                )}
              </div>
              )}
              <div className="grid grid-cols-2 gap-4">
               <div className="flex flex-col gap-2">
                 <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                   <DollarSignIcon className="size-3.5 text-primary" />
                   {t('form.payableAmount')}
                 </Label>
                 <div
                    className={`relative flex h-12 w-full items-center rounded-2xl border border-border-light bg-primary-soft/30 px-3.5 pr-10 shadow-xs ring-1 ring-primary/10 cursor-text focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/15 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] ${backfillErrors.payableAmount ? 'border-destructive ring-destructive/10 focus-within:border-destructive focus-within:ring-destructive/15' : ''}`}
                   onClick={() => setBackfillFocusedField('payableAmount')}
                   tabIndex={0}
                   onFocus={() => setBackfillFocusedField('payableAmount')}
                   onBlur={() => setBackfillFocusedField((prev) => (prev === 'payableAmount' ? null : prev))}
                    onKeyDown={(e) => {
                      if (e.nativeEvent.isComposing) return;
                      if (e.key === 'Enter') e.preventDefault();
                    }}
                  >
                   <span className={`truncate text-base font-semibold tabular-nums sm:text-lg transition-colors duration-300 ${backfillCalculatedPayable !== '' ? 'text-primary' : 'text-muted-foreground/50'}`}>
                      {backfillCalculatedPayable !== '' ? `¥${backfillCalculatedPayable}` : t('form.pendingCalc')}
                    </span>
                   {backfillFocusedField === 'payableAmount' && backfillCalculatedPayable !== '' && (
                     <button
                       type="button"
                       aria-label="清除"
                       onMouseDown={(e) => e.preventDefault()}
                       onClick={() => handleBackfillClear('payableAmount')}
                       className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                     >
                       <XIcon className="size-4" />
                     </button>
                   )}
                   {backfillFocusedField !== 'payableAmount' && backfillCalculatedPayable !== '' && !backfillErrors.payableAmount && (
                     <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                   )}
                 </div>
                 </div>

                <div className="flex flex-col gap-2">
                <Label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                  <WalletIcon className="size-3.5 text-primary" />
                  {t('form.actualAmount')}
                </Label>
                <div className="relative">
                  <Input
                    type="number"
                    inputMode="decimal"
                    step="0.01"
                    value={backfillForm.actualAmount}
                    onChange={(e) => {
                       setBackfillForm((prev) => ({ ...prev, actualAmount: e.target.value }));
                       markBackfillDirty();
                     }}
                     onFocus={() => setBackfillFocusedField('actualAmount')}
                    onBlur={() => setBackfillFocusedField((prev) => (prev === 'actualAmount' ? null : prev))}
                    onKeyDown={(e) => {
                       if (e.nativeEvent.isComposing) return;
                       if (e.key === 'Enter') e.preventDefault();
                     }}
                      placeholder="0.00"
                       className={`h-12 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-base font-semibold tabular-nums shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${backfillErrors.actualAmount ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                   />
                   {backfillFocusedField === 'actualAmount' && backfillForm.actualAmount !== '' && (
                    <button
                      type="button"
                      aria-label="清除"
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => handleBackfillClear('actualAmount')}
                       className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                    >
                      <XIcon className="size-4" />
                    </button>
                  )}
                   {backfillFocusedField !== 'actualAmount' && backfillForm.actualAmount !== '' && !Number.isNaN(Number(backfillForm.actualAmount)) && Number(backfillForm.actualAmount) > 0 && !backfillErrors.actualAmount && !isBackfillActualAbnormal && (
                     <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                   )}
                 </div>
                 </div>
                </div>

               {/* 实付金额综合信息块 */}
               <PaymentSummaryCard
                 oilAmount={Number(backfillForm.oilAmount)}
                 payableAmount={backfillCalculatedPayable !== '' ? Number(backfillCalculatedPayable) : (backfillForm.payableAmount !== '' && !Number.isNaN(Number(backfillForm.payableAmount)) ? Number(backfillForm.payableAmount) : null)}
                 actualAmount={Number(backfillForm.actualAmount)}
                 referencePrice={historyPriceResult?.price ?? null}
                 referenceDate={!isBackfillDateToday ? format(backfillForm.reoilDate, 'yyyy-MM-dd') : null}
                 referenceNote={
                   historyPriceResult?.price
                     ? historyPriceResult.pendingAfter && historyPriceResult.date
                       ? `该日期后暂无已核验新价，最近确认价（${historyPriceResult.date}）`
                       : null
                     : !historyPriceLoading && !isBackfillDateToday
                       ? '暂无该日期油价数据，按实时油价估算'
                       : null
                 }
                 isError={isBackfillRatioInvalid}
                 errorMessage={backfillRatioErrorMessage || null}
               />

                 {/* 应付 + 实付 通栏提示（阻断性错误） */}
                 {(backfillErrors.payableAmount || backfillErrors.actualAmount || isBackfillActualAbnormal) && (
                 <div className="mt-1.5 flex flex-col space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200" style={{ animationFillMode: 'backwards' }}>
                  {backfillErrors.payableAmount && (
                    <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                      <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                      <span className="leading-snug">{backfillErrors.payableAmount}</span>
                    </div>
                  )}
                  {backfillErrors.actualAmount && (
                    <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                      <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                      <span className="leading-snug">{backfillErrors.actualAmount}</span>
                    </div>
                  )}
                  {isBackfillActualAbnormal && (
                    <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                      <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                      <span className="leading-snug">实付金额超过1500元，请确认是否正确</span>
                    </div>
                  )}
                </div>
                 )}

              </div>

              {/* 警告汇总区 */}
             {backfillWarningCount >= 2 && (
                <div className="rounded-2xl border border-amber-500/25 bg-amber-500/15 transition-all duration-300 dark:border-amber-400/25 dark:bg-amber-500/20">
                  <button
                    type="button"
                    onClick={() => setBackfillSummaryExpanded((prev) => !prev)}
                    className="flex w-full items-center gap-2 rounded-2xl px-3 py-2 text-left text-[11px] font-medium text-amber-600 transition-colors dark:text-amber-400"
                  >
                    <AlertTriangleIcon className="size-3.5 shrink-0" />
                    <span className="flex-1">
                      有 {backfillWarningCount} 项需确认
                    </span>
                    <ChevronDownIcon className={`size-3.5 shrink-0 transition-transform duration-200 ${backfillSummaryExpanded ? 'rotate-180' : ''}`} />
                  </button>
                  {backfillSummaryExpanded && (
                    <div className="border-t border-amber-500/20 px-3 py-2 dark:border-amber-400/20">
                      <div className="flex flex-col gap-1.5">
                        {isBackfillMileageBackwards && (
                          <div className="flex items-start gap-1.5 text-[11px] text-amber-600 dark:text-amber-400">
                            <AlertCircleIcon className="mt-[1px] size-3 shrink-0" />
                            <span>{t('form.mileageBackwardWarn')}</span>
                          </div>
                        )}
                        {isBackfillFuelAbnormal && (
                          <div className="flex items-start gap-1.5 text-[11px] text-amber-600 dark:text-amber-400">
                            <AlertCircleIcon className="mt-[1px] size-3 shrink-0" />
                            <span>{t('form.oilAmountTooLarge')}</span>
                          </div>
                        )}
                         {isBackfillActualAbnormal && (
                           <div className="flex items-start gap-1.5 text-[11px] text-amber-600 dark:text-amber-400">
                             <AlertCircleIcon className="mt-[1px] size-3 shrink-0" />
                             <span>{t('form.actualAmountTooLarge')}</span>
                           </div>
                         )}
                      </div>
                    </div>
                  )}
                </div>
              )}
           </BounceScroll>

            {backfillDateOpen && backfillCalStyle && (
             <div
               className="absolute z-50"
               ref={backfillCalRef}
               style={{
                 top: backfillCalStyle.top,
                 left: backfillCalStyle.left,
                 width: backfillCalStyle.width,
               }}
             >
               <KoilCalendar
                 value={format(backfillForm.reoilDate, 'yyyy-MM-dd')}
                 onChange={(dateStr) => {
                   const [y, m, d] = dateStr.split('-').map(Number);
                   setBackfillForm((prev) => ({ ...prev, reoilDate: new Date(y, m - 1, d) }));
                   markBackfillDirty();
                   setBackfillDateOpen(false);
                 }}
                 max={format(new Date(), 'yyyy-MM-dd')}
                 locale={language}
                 variant="glass"
               />
             </div>
           )}

          <DialogFooter className="flex-row gap-2 px-6 pb-6 pt-2 sm:px-7 sm:pb-7">
            <Button
              type="button"
              variant="outline"
              onClick={handleBackfillClose}
              disabled={backfillSubmitting}
              className="flex-1 rounded-full border-border-light bg-[var(--glass-bg-strong)] text-foreground-secondary"
            >
              {t('common.cancel')}
            </Button>
            <Button
              type="button"
              onClick={() => void handleSubmitBackfill()}
              disabled={backfillSubmitting || backfillBoundary?.isBeforeFirst}
              className="flex-1 rounded-full gradient-primary text-white shadow-lg shadow-black/25"
            >
              {backfillSubmitting ? t('backfill.submitting') : t('backfill.submit')}
            </Button>
          </DialogFooter>
              </div>
          </DialogContent>
       </Dialog>


      </>
  );
}

export default RecordList;
