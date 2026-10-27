import { motion } from 'framer-motion';
import type { ReactNode } from 'react';
import { Droplet as OilDropIcon } from 'lucide-react';
import { useTranslation } from '@client/src/hooks/useTranslation';
import { usePriceAlert } from '@client/src/hooks/usePriceAlert';
import type { OilPriceInfo } from '@shared/api.interface';

interface OilPriceCardProps {
  province: string;
  priceData?: OilPriceInfo[] | null;
  updateDate?: string;
  nextAdjustment?: string;
  forecast?: string;
  loading?: boolean;
  source?: string;
  compact?: boolean;
  headerRight?: ReactNode;
}

function daysUntil(dateStr: string): number {
  const target = new Date(dateStr + 'T00:00:00');
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const diffMs = target.getTime() - today.getTime();
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
}

function parseAdjustmentDate(nextAdjustment: string): string | null {
  const match = nextAdjustment.match(/(\d{1,2})月(\d{1,2})日/);
  if (!match) return null;
  const month = match[1].padStart(2, '0');
  const day = match[2].padStart(2, '0');
  const now = new Date();
  const year = now.getMonth() + 1 > Number(month) ? now.getFullYear() + 1 : now.getFullYear();
  return `${year}-${month}-${day}`;
}

function formatAdjustmentTime(nextAdjustment: string): string {
  const match = nextAdjustment.match(/(\d{1,2})月(\d{1,2})日/);
  if (!match) return nextAdjustment;
  return `${match[1]}月${match[2]}日 24:00`;
}

type Trend = 'up' | 'down' | 'flat';

function getForecastTrend(forecast?: string): Trend {
  if (!forecast) return 'flat';
  if (forecast.includes('上调') || forecast.includes('上涨') || forecast.includes('涨')) return 'up';
  if (forecast.includes('下调') || forecast.includes('下跌') || forecast.includes('降')) return 'down';
  return 'flat';
}

function extractForecastAmount(forecast?: string): string | null {
  if (!forecast) return null;
  const match = forecast.match(/([\d.]+)\s*元\/吨/);
  if (match) return match[1];
  return null;
}

const GASOLINE_DENSITY_KG_PER_L = 0.725;

function convertPerTonToPerLiter(amountStr: string): number {
  const amount = Number(amountStr);
  if (!Number.isFinite(amount) || amount <= 0) return 0;
  return amount / 1000 * GASOLINE_DENSITY_KG_PER_L;
}

const OilPriceCard = ({
  province,
  priceData,
  updateDate,
  nextAdjustment,
  forecast,
  loading = false,
  source,
  compact = false,
  headerRight,
}: OilPriceCardProps) => {
  const { t } = useTranslation();
  const { enabled: priceAlertEnabled } = usePriceAlert();

  if (loading) return null;

  const hasData = priceData != null && priceData.length > 0;
  const safePriceData = priceData ?? [];

  const adjustDateStr = nextAdjustment ? parseAdjustmentDate(nextAdjustment) : null;
  const days = adjustDateStr ? daysUntil(adjustDateStr) : -999;
  const isUrgent = !hasData ? false : (adjustDateStr != null && days <= 3 && days >= 0);
  const forecastTrend = getForecastTrend(forecast);
  const forecastAmount = extractForecastAmount(forecast);
  const perLiterEquivalent = forecastAmount ? convertPerTonToPerLiter(forecastAmount) : 0;

  const forecastLabel = forecast
    ? (forecastTrend === 'up'
        ? t('oilPrice.forecastUp', { amount: `${forecastAmount ?? ''}元/吨` })
        : forecastTrend === 'down'
          ? t('oilPrice.forecastDown', { amount: `${forecastAmount ?? ''}元/吨` })
          : forecast)
    : '';

  const suggestion = forecastTrend === 'down'
    ? t('oilPrice.suggestWait')
    : forecastTrend === 'up'
      ? t('oilPrice.suggestBuyEarly')
      : '';

  const paddingClass = compact ? 'p-2.5' : 'p-4';

  const sortedGrades = ['92', '95', '98', '0'];

  const gradeMap = new Map<string, OilPriceInfo>();
  for (const item of safePriceData) {
    gradeMap.set(item.grade, item);
  }

  const displayGrades = sortedGrades.filter((g: string) =>
    gradeMap.has(g),
  );
  const displayGradesOrFallback = hasData ? displayGrades : sortedGrades;

  const trendColors = {
    up: 'text-amber-500 dark:text-amber-400',
    down: 'text-emerald-500 dark:text-emerald-400',
    flat: 'text-muted-foreground',
  };

  const trendBgColors = {
    up: 'bg-amber-500/10 text-amber-600 dark:text-amber-400',
    down: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400',
    flat: 'bg-muted text-muted-foreground',
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: -4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}
      className={`glass-soft flex min-w-0 flex-1 flex-col gap-4 rounded-2xl ${paddingClass} backdrop-blur-md transition-all duration-300 ${
        isUrgent ? 'ring-2 ring-primary/40' : ''
      }`}
    >
      <div className="flex items-center gap-2.5">
        <div
          className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${
            isUrgent ? 'gradient-primary' : 'bg-primary-soft'
          }`}
        >
          <OilDropIcon
            className={`size-4.5 ${isUrgent ? 'text-white' : 'text-primary'}`}
          />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-[13px] font-semibold text-foreground">
            {province}油价
          </p>
          <p className="mt-0.5 truncate text-[10px] text-muted-foreground">
            {updateDate ? `${source || '更新于'} ${updateDate}` : hasData ? '' : t('oilPrice.loading')}
          </p>
        </div>
        {headerRight}
      </div>

      <div className="flex flex-col gap-2">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {displayGradesOrFallback.map((grade: string) => {
              const item = gradeMap.get(grade);
              const gradeUnit = grade === '0' ? '柴油' : '#';
              return (
                <div
                  key={grade}
                  className="flex flex-col gap-1 px-0 py-1.5"
                >
                  <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground/70">
                    {grade}{gradeUnit}
                  </span>
                  <div className="flex items-baseline gap-0.5">
                    <span className="text-[11px] font-medium text-muted-foreground/60">¥</span>
                    {item ? (
                      <>
                        <span className="text-xl font-bold tabular-nums text-foreground leading-tight">
                          {item.price.toFixed(2)}
                        </span>
                        <span className="text-[10px] text-muted-foreground/60">
                          元/升
                        </span>
                      </>
                    ) : (
                      <span className="text-xl font-bold tabular-nums text-muted-foreground/30 leading-tight">
                        --
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
        </div>

          {priceAlertEnabled && hasData && (nextAdjustment || forecast || suggestion) && (
          <div className="flex flex-col gap-1.5 border-t border-border/30 pt-2">
            {nextAdjustment && (
              <p className="text-[11px] font-medium text-muted-foreground">
                {t('oilPrice.nextAdjustWindow', { date: formatAdjustmentTime(nextAdjustment) })}
              </p>
            )}
            {(forecast || (forecastAmount && perLiterEquivalent > 0) || suggestion) && (
              <div className="flex flex-wrap items-center gap-1.5 text-[11px] font-medium">
                {forecast && (
                  <div
                    className={`inline-flex items-center rounded-md px-2 py-0.5 ${trendBgColors[forecastTrend]}`}
                  >
                    <span>{forecastLabel}</span>
                  </div>
                )}
                {forecastAmount && perLiterEquivalent > 0 && (
                  <span className="text-muted-foreground">
                    {t('oilPrice.perLiterEquivalent', { amount: perLiterEquivalent.toFixed(2) })}
                  </span>
                )}
                {((forecastAmount && perLiterEquivalent > 0) || forecast) && suggestion && (
                  <span className="text-border/60">/</span>
                )}
                {suggestion && (
                  <span className={trendColors[forecastTrend]}>
                    {suggestion}
                  </span>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </motion.div>
  );
};

export { OilPriceCard };
