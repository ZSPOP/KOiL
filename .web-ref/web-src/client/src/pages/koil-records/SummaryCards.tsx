import { useEffect, useState, useRef, useMemo, useLayoutEffect } from 'react';
import {
  Info,
  TrendingUp,
  TrendingDown,
  Wallet,
  Gauge,
  Droplets,
  Route,
  Clock,
  DollarSign,
} from 'lucide-react';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';
import type {
  KoilRecordWithDerived,
} from '@shared/api.interface';
import { AbnormalAlertCard } from './AbnormalDialog';
export { AbnormalAlertCard } from './AbnormalDialog';
import { useTranslation } from '@client/src/hooks/useTranslation';

type RangeKey = 'all' | 'year' | '6m' | '3m';

const RANGE_KEYS: RangeKey[] = ['all', 'year', '6m', '3m'];

const RANGE_LABEL_KEY: Record<RangeKey, string> = {
  all: 'range.all',
  year: 'range.thisYear',
  '6m': 'range.last6Months',
  '3m': 'range.last3Months',
};

interface SummaryCardsProps {
  items: KoilRecordWithDerived[];
  loading: boolean;
  range: string;
  onRangeChange: (range: string) => void;
  abnormalThreshold?: number;
  hideAbnormalCard?: boolean;
}

/* ---------- 工具函数 ---------- */

function fmt(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '-';
  return value.toFixed(2);
}

function fmtInt(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '-';
  return String(Math.round(value));
}

/* ---------- 计算口径 ---------- */

interface RangeMetrics {
  totalDistance: number;
  totalOilAmount: number;
  totalActual: number;
  avgOilConsumption: number;
  avgCostPerKm: number;
  avgOilDays: number;
  abnormalCount: number;
  count: number;
  startDate: Date | null;
  endDate: Date | null;
}

function computeMetrics(records: KoilRecordWithDerived[]): RangeMetrics {
  const sorted = [...records].sort(
    (a, b) => new Date(a.reoilDate).getTime() - new Date(b.reoilDate).getTime(),
  );

  let totalDistance = 0;
  let totalOilAmount = 0;
  let totalActual = 0;
  let totalOilDays = 0;
  let oilDaysCount = 0;
  let abnormalCount = 0;
  let segmentFuelAmount = 0;
  let segmentActualAmount = 0;

  sorted.forEach((r) => {
    totalOilAmount += r.oilAmount;
    totalActual += r.actualAmount;
    if (r.isAbnormalOilConsumption) abnormalCount += 1;
    if (r.distance != null) {
      totalDistance += r.distance;
      segmentFuelAmount += r.oilAmount;
      segmentActualAmount += r.actualAmount;
    }
    if (r.oilDays != null) {
      totalOilDays += r.oilDays;
      oilDaysCount += 1;
    }
  });

  const avgOilConsumption =
    totalDistance > 0 && segmentFuelAmount > 0
      ? (segmentFuelAmount / totalDistance) * 100
      : 0;
  const avgCostPerKm = totalDistance > 0 && segmentActualAmount > 0 ? segmentActualAmount / totalDistance : 0;
  const avgOilDays = oilDaysCount > 0 ? totalOilDays / oilDaysCount : 0;

  const startDate = sorted.length > 0 ? new Date(sorted[0].reoilDate) : null;
  const endDate =
    sorted.length > 0 ? new Date(sorted[sorted.length - 1].reoilDate) : null;

  return {
    totalDistance,
    totalOilAmount,
    totalActual,
    avgOilConsumption,
    avgCostPerKm,
    avgOilDays,
    abnormalCount,
    count: sorted.length,
    startDate,
    endDate,
  };
}

function getRangeStart(range: RangeKey): Date {
  const now = new Date();
  switch (range) {
    case 'year': {
      return new Date(now.getFullYear(), 0, 1);
    }
    case '6m': {
      const d = new Date(now);
      d.setMonth(d.getMonth() - 6);
      return d;
    }
    case '3m': {
      const d = new Date(now);
      d.setMonth(d.getMonth() - 3);
      return d;
    }
    case 'all':
    default:
      return new Date(0);
  }
}

function filterByRange(
  items: KoilRecordWithDerived[],
  range: RangeKey,
): KoilRecordWithDerived[] {
  if (range === 'all') return items;
  const start = getRangeStart(range);
  return items.filter((r) => new Date(r.reoilDate) >= start);
}

function getPrevPeriodRecords(
  items: KoilRecordWithDerived[],
  range: RangeKey,
): KoilRecordWithDerived[] {
  if (range === 'all') return [];

  const current = filterByRange(items, range);
  if (current.length === 0) return [];

  const rangeStart = getRangeStart(range);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());

  let prevStart: Date;
  let prevEnd: Date;

  if (range === 'year') {
    const yearStart = new Date(today.getFullYear(), 0, 1);
    prevStart = new Date(today.getFullYear() - 1, 0, 1);
    prevEnd = yearStart;
  } else {
    const months = range === '6m' ? 6 : 3;
    prevEnd = rangeStart;
    prevStart = new Date(rangeStart);
    prevStart.setMonth(prevStart.getMonth() - months);
  }

  return items.filter((r) => {
    const t = new Date(r.reoilDate).getTime();
    return t >= prevStart.getTime() && t < prevEnd.getTime();
  });
}

function calcPctChange(current: number, prev: number): number | null {
  if (prev === 0) return null;
  return ((current - prev) / Math.abs(prev)) * 100;
}

/* ---------- 油价调价提醒 ---------- */
/* 油价卡片已迁移至录入页（RecordForm），此处不再展示 */

/* ---------- 指标对比徽章 ---------- */

interface CompareBadgeProps {
  pct: number | null;
  direction: 'lower-is-better' | 'higher-is-better';
  isLarge?: boolean;
}

function CompareBadge({ pct, direction, isLarge = false }: CompareBadgeProps) {
  void isLarge;
  if (pct === null) {
    return (
      <span className="inline-flex shrink-0 items-center font-medium tabular-nums text-[11px] text-muted-foreground sm:text-xs">
        -
      </span>
    );
  }
  const isUp = pct >= 0;
  const absPct = Math.abs(pct);
  const isGood =
    direction === 'lower-is-better' ? !isUp : isUp;
  const colorClass = isGood ? 'text-success' : 'text-primary';
  const icon = isUp ? (
    <TrendingUp className="size-3" />
  ) : (
    <TrendingDown className="size-3" />
  );

  return (
    <span className={`inline-flex shrink-0 items-center gap-0.5 font-semibold tabular-nums text-[11px] sm:text-xs ${colorClass}`}>
      {icon}
      {absPct.toFixed(1)}%
    </span>
  );
}

/* ---------- 指标单元 ---------- */

interface MetricCellProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  unit: string;
  comparePct: number | null;
  direction: 'lower-is-better' | 'higher-is-better';
  size?: 'sm' | 'lg';
}

function MetricCell({ icon, label, value, unit, comparePct, direction, size = 'sm' }: MetricCellProps) {
  const { t } = useTranslation();
  void size;

  return (
    <div
      className="glass-soft relative flex min-h-[96px] flex-col justify-between overflow-hidden rounded-2xl p-3 transition-[transform,background-color,box-shadow] duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] sm:min-h-[120px] sm:p-5"
    >
      <div className="flex items-start justify-between gap-2">
        <span className="truncate text-sm font-medium text-foreground sm:text-[15px]">
          {label}
        </span>
        <span className="flex size-5 shrink-0 items-center justify-center text-muted-foreground/70 sm:size-6">
          {icon}
        </span>
      </div>

      <div className="flex flex-col gap-1.5 sm:gap-2">
        <div className="flex min-w-0 items-baseline gap-1">
          <span className="whitespace-nowrap font-bold tabular-nums tracking-tight text-lg leading-none text-foreground sm:text-xl">
            {value}
          </span>
          <span className="shrink-0 text-[11px] font-medium text-muted-foreground sm:text-xs">
            {unit}
          </span>
        </div>
        <div className="flex min-w-0 items-center justify-between gap-1.5">
          <span className="shrink-0 text-[11px] font-medium text-muted-foreground/70 sm:text-xs">
            {t('metric.compareToPrev')}
          </span>
          <CompareBadge pct={comparePct} direction={direction} />
        </div>
      </div>
    </div>
  );
}

function HeroMetricCell({ metric }: { metric: HeroMetric }) {
  return (
    <MetricCell
      icon={metric.icon}
      label={metric.label}
      value={metric.value}
      unit={metric.unit}
      comparePct={metric.comparePct}
      direction={metric.direction}
    />
  );
}

/* ---------- 总览卡 ---------- */

interface HeroMetric {
  icon: React.ReactNode;
  label: string;
  value: string;
  unit: string;
  comparePct: number | null;
  direction: 'lower-is-better' | 'higher-is-better';
}

interface SupportMetric {
  key: string;
  icon: React.ReactNode;
  label: string;
  value: string;
  unit: string;
  comparePct: number | null;
  direction: 'lower-is-better' | 'higher-is-better';
}

function OverviewCard({
  heroMetric,
  secondMetric,
  supports,
  count,
  range,
  onRangeChange,
}: {
  heroMetric: HeroMetric;
  secondMetric: HeroMetric;
  supports: SupportMetric[];
  count: number;
  range: RangeKey;
  onRangeChange: (range: string) => void;
}) {
  const { t } = useTranslation();

  return (
    <div className="glass-soft overflow-hidden rounded-[28px] p-4 sm:p-6">
      <div className="mb-4 flex items-center justify-between sm:mb-5">
        <h3 className="text-sm font-semibold text-foreground sm:text-base">
          {t('metric.summaryTitle')}
        </h3>
        <div className="flex rounded-full bg-muted-foreground/10 p-1">
          {RANGE_KEYS.map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => onRangeChange(key)}
              className={`rounded-full px-3 py-1 text-[11px] font-medium transition-all duration-200 sm:px-3.5 sm:text-xs ${
                range === key
                  ? 'gradient-primary text-white shadow-sm'
                  : 'text-muted-foreground'
              }`}
            >
              {t(RANGE_LABEL_KEY[key])}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:gap-4">
        <HeroMetricCell metric={heroMetric} />
        <HeroMetricCell metric={secondMetric} />
      </div>

      <div className="mt-3 grid grid-cols-2 gap-3 sm:mt-4 sm:grid-cols-4 sm:gap-3">
        {supports.map((m) => (
          <MetricCell
            key={m.key}
            icon={m.icon}
            label={m.label}
            value={m.value}
            unit={m.unit}
            comparePct={m.comparePct}
            direction={m.direction}
          />
        ))}
      </div>

      <div className="mt-3 flex items-center justify-between border-t border-border/60 pt-3 text-[11px] text-muted-foreground sm:mt-4 sm:pt-4 sm:text-xs">
        <span>{t('metric.reoilCount', { count })}</span>
        <span className="font-medium text-muted-foreground/70">
          {t('metric.compareToPrev')}
        </span>
      </div>
    </div>
  );
}

/* ---------- 骨架屏 ---------- */

function SkeletonOverviewCard() {
  return (
    <div className="glass-soft animate-pulse overflow-hidden rounded-[28px] p-4 sm:p-6">
      <div className="mb-4 flex items-center justify-between sm:mb-5">
        <div className="h-4 w-24 rounded-full bg-muted-foreground/10" />
        <div className="h-7 w-40 rounded-full bg-muted-foreground/10" />
      </div>
      <div className="grid grid-cols-2 gap-3 sm:gap-4">
        <div className="h-24 rounded-2xl bg-muted-foreground/10 sm:h-28" />
        <div className="h-24 rounded-2xl bg-muted-foreground/10 sm:h-28" />
      </div>
      <div className="mt-3 grid grid-cols-4 gap-3 sm:mt-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-20 rounded-2xl bg-muted-foreground/10" />
        ))}
      </div>
      <div className="mt-3 h-3 w-full rounded-full bg-muted-foreground/10 sm:mt-4" />
    </div>
  );
}

function SkeletonInsightRow() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-3 sm:gap-4">
      <div className="glass-soft flex w-full items-center gap-3 rounded-2xl border border-border p-4 backdrop-blur-md sm:p-5">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-muted-foreground/10">
          <Info className="size-5 text-muted-foreground/30" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="h-4 w-32 animate-pulse rounded-full bg-muted-foreground/10" />
          <div className="mt-1.5 h-3 w-48 animate-pulse rounded-full bg-muted-foreground/10" />
        </div>
      </div>
    </div>
  );
}

/* ---------- 主组件 ---------- */

export function SummaryCards({
  items,
  loading,
  range,
  onRangeChange,
  abnormalThreshold = 25,
  hideAbnormalCard = false,
}: SummaryCardsProps) {
  const { t } = useTranslation();
  const [rangeKey, setRangeKey] = useState<RangeKey>(range as RangeKey);
  const [displayItems, setDisplayItems] = useState<KoilRecordWithDerived[]>([]);
  const [displayMetrics, setDisplayMetrics] = useState<RangeMetrics | null>(null);
  const [prevMetrics, setPrevMetrics] = useState<RangeMetrics | null>(null);
  const [transitioning, setTransitioning] = useState(false);
  const prevRangeRef = useRef<RangeKey>(rangeKey);
  const rafRef = useRef<number | null>(null);

  // 初始化
  useLayoutEffect(() => {
    const current = filterByRange(items, rangeKey);
    const currentMetrics = computeMetrics(current);
    const prevItems = getPrevPeriodRecords(items, rangeKey);
    const prev = computeMetrics(prevItems);
    setDisplayItems(current);
    setDisplayMetrics(currentMetrics);
    setPrevMetrics(prev);
  }, [items, rangeKey]);

  const handleRangeChange = (newRange: string): void => {
    const next = newRange as RangeKey;
    if (next === rangeKey) return;
    prevRangeRef.current = rangeKey;
    setTransitioning(true);
    setRangeKey(next);
    onRangeChange(newRange);
  };

  useEffect(() => {
    if (!transitioning) return;
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    rafRef.current = requestAnimationFrame(() => {
      const current = filterByRange(items, rangeKey);
      const m = computeMetrics(current);
      const prevItems = getPrevPeriodRecords(items, rangeKey);
      const p = computeMetrics(prevItems);
      setDisplayItems(current);
      setDisplayMetrics(m);
      setPrevMetrics(p);
      setTimeout(() => setTransitioning(false), 350);
    });
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [transitioning, items, rangeKey]);

  const currentItems = displayItems;
  const currentMetrics = displayMetrics ?? {
    totalDistance: 0, totalOilAmount: 0, totalActual: 0,
    avgOilConsumption: 0, avgCostPerKm: 0, avgOilDays: 0,
    abnormalCount: 0, count: 0, startDate: null, endDate: null,
  };
  const prev = prevMetrics ?? null;

  const hasData = items.length > 0;
  const abnormalCount = currentMetrics.abnormalCount;

  const rangeLabel = (() => {
    if (rangeKey === 'all') return t('range.all');
    if (!currentMetrics.startDate || !currentMetrics.endDate) return '';
    const s = currentMetrics.startDate.toLocaleDateString();
    const e = currentMetrics.endDate.toLocaleDateString();
    return `${s} ~ ${e}`;
  })();

  const heroMetric: HeroMetric = useMemo(() => ({
    icon: <Gauge className="size-4" />,
    label: t('metric.avgOilConsumption'),
    value: currentMetrics.avgOilConsumption > 0 ? fmt(currentMetrics.avgOilConsumption) : '—',
    unit: t('unit.lPer100km'),
    comparePct: prev && prev.avgOilConsumption > 0
      ? calcPctChange(currentMetrics.avgOilConsumption, prev.avgOilConsumption)
      : null,
    direction: 'lower-is-better',
  }), [currentMetrics, prev, t]);

  const secondMetric: HeroMetric = useMemo(() => ({
    icon: <DollarSign className="size-4" />,
    label: t('metric.costPerKm'),
    value: currentMetrics.avgCostPerKm > 0 ? currentMetrics.avgCostPerKm.toFixed(3) : '—',
    unit: t('unit.yuanPerKm'),
    comparePct: prev && prev.avgCostPerKm > 0
      ? calcPctChange(currentMetrics.avgCostPerKm, prev.avgCostPerKm)
      : null,
    direction: 'lower-is-better',
  }), [currentMetrics, prev, t]);

  const supportMetrics: SupportMetric[] = useMemo(() => [
    {
      key: 'distance',
      icon: <Route className="size-4" />,
      label: t('metric.totalDistance'),
      value: fmtInt(currentMetrics.totalDistance),
      unit: t('unit.km'),
      comparePct: prev && prev.totalDistance > 0
        ? calcPctChange(currentMetrics.totalDistance, prev.totalDistance)
        : null,
      direction: 'higher-is-better',
    },
    {
      key: 'fuel',
      icon: <Droplets className="size-4" />,
      label: t('metric.totalOil'),
      value: fmtInt(currentMetrics.totalOilAmount),
      unit: t('unit.liter'),
      comparePct: prev && prev.totalOilAmount > 0
        ? calcPctChange(currentMetrics.totalOilAmount, prev.totalOilAmount)
        : null,
      direction: 'higher-is-better',
    },
    {
      key: 'cost',
      icon: <Wallet className="size-4" />,
      label: t('metric.totalCost'),
      value: `¥${fmtInt(currentMetrics.totalActual)}`,
      unit: '',
      comparePct: prev && prev.totalActual > 0
        ? calcPctChange(currentMetrics.totalActual, prev.totalActual)
        : null,
      direction: 'higher-is-better',
    },
    {
      key: 'frequency',
      icon: <Clock className="size-4" />,
      label: t('metric.avgOilDays'),
      value: currentMetrics.avgOilDays > 0 ? currentMetrics.avgOilDays.toFixed(0) : '—',
      unit: t('unit.day'),
      comparePct: prev && prev.avgOilDays > 0
        ? calcPctChange(currentMetrics.avgOilDays, prev.avgOilDays)
        : null,
      direction: 'lower-is-better',
    },
  ], [currentMetrics, prev, t]);

  return (
    <div className="flex flex-col gap-4 sm:gap-5">
      {/* 总览卡（卡内时间筛选 + 双核心数据） */}
      {loading ? (
        <SkeletonOverviewCard />
      ) : hasData ? (
        <OverviewCard
          heroMetric={heroMetric}
          secondMetric={secondMetric}
          supports={supportMetrics}
          count={currentMetrics.count}
          range={rangeKey}
          onRangeChange={handleRangeChange}
        />
      ) : (
        <div className="glass-soft flex flex-col items-center justify-center rounded-[28px] px-4 py-12 text-center sm:py-16">
          <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-primary-soft">
            <Info className="h-7 w-7 text-primary" />
          </div>
          <p className="text-sm font-medium text-foreground-secondary sm:text-base">
             {t('empty.noRecordsOverview')}
           </p>
           <p className="mt-1.5 text-xs text-muted-foreground sm:text-sm">
              {t('empty.noRecordsOverviewDesc')}
            </p>
        </div>
      )}

      {/* 洞察区 */}
      {loading ? (
        <SkeletonInsightRow />
      ) : (
        <div className="flex flex-col gap-3 transition-opacity duration-200 sm:gap-4">
          {/* 异常状态卡（始终整行展示） */}
          {hasData && !hideAbnormalCard && (
            <AbnormalAlertCard
              count={abnormalCount}
              threshold={abnormalThreshold}
              items={currentItems}
              avgConsumption={currentMetrics.avgOilConsumption}
              rangeLabel={rangeLabel}
              recordCount={currentItems.length}
            />
          )}
          {/* 油价提示已移至录入页 */}
        </div>
      )}
    </div>
  );
}
