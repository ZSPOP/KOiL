import { useState } from 'react';
import { AlertTriangle, ArrowUpRight, X, TrendingUp, TrendingDown, ChevronRight, CheckCircle2 } from 'lucide-react';
import { useTranslation } from '@client/src/hooks/useTranslation';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@client/src/components/ui/dialog';

import type { KoilRecordWithDerived } from '@shared/api.interface';
import { BounceScroll } from '@client/src/components/ui/bounce-scroll';

interface AbnormalAlertCardProps {
  count: number;
  threshold: number;
  items: KoilRecordWithDerived[];
  avgConsumption: number;
  rangeLabel?: string;
  recordCount?: number;
}

function getAbnormalRecords(
  items: KoilRecordWithDerived[],
): (KoilRecordWithDerived & { deviationPct: number })[] {
  const consumptions = items
    .map((r) => r.oilConsumption)
    .filter((v): v is number => v != null && v > 0);
  if (consumptions.length === 0) return [];
  const avg = consumptions.reduce((sum, v) => sum + v, 0) / consumptions.length;
  if (avg === 0) return [];
  return items
    .filter((r) => r.isAbnormalOilConsumption && r.oilConsumption != null)
    .map((r) => ({
      ...r,
      deviationPct: ((r.oilConsumption! - avg) / avg) * 100,
    }))
    .sort((a, b) => Math.abs(b.deviationPct) - Math.abs(a.deviationPct));
}

export function AbnormalAlertCard({ count, threshold, items, avgConsumption, rangeLabel, recordCount }: AbnormalAlertCardProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  if (count === 0) {
    return (
      <motion.div
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}
        className="glass-soft flex w-full items-center gap-3 rounded-2xl border border-success/30 bg-success/10 p-4 backdrop-blur-md sm:p-5"
      >
        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-success/20">
          <CheckCircle2 className="size-5 text-success" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-foreground">
            {t('insight.noAbnormal')}
          </p>
          <p className="mt-1 text-xs text-muted-foreground sm:text-[13px]">
            {t('insight.noAbnormalDesc')}
          </p>
        </div>
        {rangeLabel && (
          <div className="shrink-0 text-right">
            <p className="text-xs font-medium text-success">{rangeLabel}</p>
            {recordCount != null && (
              <p className="mt-0.5 text-[11px] text-muted-foreground">
                {t('insight.reoilCount', { count: recordCount })}
              </p>
            )}
          </div>
        )}
      </motion.div>
    );
  }

  const abnormalItems = getAbnormalRecords(items);

  return (
    <>
      <motion.button
        type="button"
        initial={{ opacity: 0, y: -4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}
        onClick={() => setOpen(true)}
        className="glass-soft group flex w-full items-center gap-3 rounded-2xl border border-border p-4 text-left backdrop-blur-md transition-all sm:p-5"
        aria-label={t('insight.viewDetailAria', { count })}
      >
        <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft transition-transform">
          <AlertTriangle className="size-5 text-primary" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-foreground">
            {t('insight.abnormalTitle', { count })}
          </p>
          <p className="mt-1 text-xs text-muted-foreground sm:text-[13px]">
            {t('insight.abnormalDesc')}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-1 text-xs font-medium text-primary opacity-80 transition-all">
          {t('insight.viewDetail')}
          <ChevronRight className="size-3.5" />
        </div>
      </motion.button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          showCloseButton={false}
          className="max-w-lg overflow-hidden p-0"
        >
          <div className="relative px-6 pb-2 pt-6 sm:px-7 sm:pt-7">
            <DialogHeader className="text-left">
              <DialogTitle className="flex items-center gap-2 text-xl font-bold text-foreground">
                <AlertTriangle className="size-5 text-amber-500" />
                  {t('insight.abnormalDetailTitle')}
              </DialogTitle>
              <p className="mt-1 text-xs text-muted-foreground">
                {t('insight.abnormalSummary', { count: abnormalItems.length, threshold })}
              </p>
            </DialogHeader>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="absolute right-5 top-5 flex size-8 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200"
              aria-label={t('common.close')}
            >
              <X className="size-4" />
            </button>
          </div>

          <BounceScroll className="max-h-[65vh] space-y-2 px-6 py-4 sm:px-7">
            <AnimatePresence initial={false}>
              {abnormalItems.map((record, idx) => {
                const isHigh = record.oilConsumptionLevel === 'high';
                const absPct = Math.abs(record.deviationPct).toFixed(1);
                return (
                  <motion.div
                    key={record.id}
                    initial={{ opacity: 0, y: 8 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.25, delay: idx * 0.03, ease: [0.2, 0.8, 0.2, 1] }}
                    className="glass-soft flex items-center gap-3 rounded-2xl p-3.5"
                  >
                    <div
                      className={`flex size-11 shrink-0 items-center justify-center rounded-2xl ${
                        isHigh ? 'bg-rose-100 text-rose-500' : 'bg-sky-100 text-sky-500'
                      }`}
                    >
                      {isHigh ? (
                        <TrendingUp className="size-5" />
                      ) : (
                        <TrendingDown className="size-5" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-foreground">
                          {record.reoilDate}
                        </span>
                        <span
                          className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${
                            isHigh
                              ? 'bg-rose-100 text-rose-600'
                              : 'bg-sky-100 text-sky-600'
                          }`}
                        >
                          {isHigh ? t('insight.higher') : t('insight.lower')} {isHigh ? '+' : '-'}{absPct}%
                        </span>
                      </div>
                      <div className="mt-1 flex items-baseline gap-1">
                        <span className="text-base font-bold tabular-nums text-foreground">
                          {record.oilConsumption?.toFixed(2)}
                        </span>
                        <span className="text-[11px] text-muted-foreground">{t('unit.lPer100km')}</span>
                        <span className="mx-2 text-[11px] text-muted-foreground">·</span>
                        <span className="text-[11px] text-muted-foreground">
                          {t('insight.avgLabel', { value: avgConsumption.toFixed(2) })}
                        </span>
                      </div>
                      {record.distance != null && (
                        <p className="mt-0.5 text-[11px] text-muted-foreground">
                          {t('insight.distanceLabel', { value: record.distance.toLocaleString() })}
                        </p>
                      )}
                    </div>
                    <ArrowUpRight className="size-4 shrink-0 text-muted-foreground/60" />
                  </motion.div>
                );
              })}
            </AnimatePresence>
          </BounceScroll>

          <div className="px-6 pb-6 pt-2 sm:px-7 sm:pb-7">
            <p className="text-center text-[11px] text-muted-foreground">
              {t('insight.abnormalRule', { threshold })}
            </p>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
