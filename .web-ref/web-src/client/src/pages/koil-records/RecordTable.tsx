import { useState } from 'react';
import { Trash2Icon } from 'lucide-react';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';

import { Button } from '@client/src/components/ui/button';
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

import { deleteKoilRecord } from '@client/src/api/koil-records';
import type { KoilRecordWithDerived } from '@shared/api.interface';
import { useTranslation } from '@client/src/hooks/useTranslation';

function fmt(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-';
  return value.toFixed(2);
}

interface RecordTableProps {
  items: KoilRecordWithDerived[];
  loading: boolean;
  onDeleted: () => void;
}

interface RecordCardProps {
  record: KoilRecordWithDerived;
  onDelete: (id: string) => void;
}

function RecordCard({ record, onDelete }: RecordCardProps) {
  const { t } = useTranslation();
  const hasDerived = record.distance !== null && record.distance !== undefined;

  return (
    <div
      className="group relative overflow-hidden rounded-2xl bg-surface-1 p-4 transition-[transform,box-shadow] duration-300 sm:p-5"
      style={{
        boxShadow: 'var(--shadow-sm)',
        border: '1px solid var(--border-light)',
      }}
    >
      {/* 顶部：日期 + 里程 + 删除按钮 */}
      <div className="mb-4 flex items-start justify-between">
        <div className="flex items-start gap-3">
          <div className="mt-0.5 flex size-2.5 shrink-0 rounded-full gradient-primary shadow-sm" />
          <div className="flex flex-col gap-1">
            <span className="text-base font-bold text-foreground sm:text-lg">
              {record.reoilDate}
            </span>
            <span className="text-xs text-muted-foreground">
              {t('record.mileageAt', { value: fmt(record.mileage) })}
            </span>
          </div>
        </div>
        <Button
          variant="ghost"
          size="icon"
          aria-label={`${t('common.delete')} ${record.reoilDate}`}
          className="size-9 rounded-full border border-transparent transition-all duration-200"
          onClick={() => onDelete(record.id)}
        >
          <Trash2Icon className="size-4 text-destructive" />
        </Button>
      </div>

      {/* 主要数据网格：加油量 + 应付 + 实付 + 单价 */}
      <div className="mb-4 grid grid-cols-2 gap-y-3 gap-x-4 text-sm sm:grid-cols-4">
        <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              {t('form.oilAmount')}
            </span>
          <span className="tabular-nums text-base font-semibold text-foreground">
            {fmt(record.oilAmount)}{' '}
            <span className="text-xs font-normal text-muted-foreground">{t('unit.liter')}</span>
          </span>
        </div>
        <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              {t('form.actualAmount')}
            </span>
          <span className="tabular-nums text-base font-semibold text-foreground">
            ¥{fmt(record.actualAmount)}
          </span>
        </div>
        <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              {t('form.payableAmount')}
            </span>
          <span className="tabular-nums font-medium text-foreground-secondary">
            ¥{fmt(record.payableAmount)}
          </span>
        </div>
        <div className="flex flex-col gap-1">
            <span className="text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
              {t('record.unitPriceActual')}
            </span>
          <span className="tabular-nums font-medium text-foreground-tertiary">
            ¥{fmt(record.unitPriceActual)}
            <span className="text-xs font-normal text-muted-foreground">/{t('unit.liter')}</span>
          </span>
        </div>
      </div>

      {/* 次数据行：单价·应付 + 行驶里程 + 用油天数 */}
      <div className="mb-4 grid grid-cols-3 gap-y-2 gap-x-4 border-t border-dashed border-border-light pt-3 text-xs sm:grid-cols-4">
        <div className="flex flex-col gap-0.5">
            <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
              {t('record.unitPricePayable')}
            </span>
          <span className="tabular-nums font-medium text-foreground-tertiary">
             ¥{fmt(record.unitPricePayable)}/{t('unit.liter')}
          </span>
        </div>
        {hasDerived ? (
          <>
            <div className="flex flex-col gap-0.5">
                <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                  {t('record.distance')}
                </span>
                <span className="tabular-nums font-medium text-foreground-tertiary">
                  {fmt(record.distance)} {t('unit.km')}
                </span>
            </div>
            <div className="flex flex-col gap-0.5">
                <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                  {t('record.oilDays')}
                </span>
                <span className="tabular-nums font-medium text-foreground-tertiary">
                  {record.oilDays} {t('unit.day')}
                </span>
            </div>
            <div className="flex flex-col gap-0.5">
                <span className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                  {t('record.avgInterval')}
                </span>
              <span className="tabular-nums font-medium text-foreground-tertiary">
                {record.oilDateRange}
              </span>
            </div>
          </>
        ) : (
          <div className="col-span-3 flex items-center text-muted-foreground sm:col-span-3">
            {t('record.noDerivedData')}
          </div>
        )}
      </div>

      {/* 派生数据高亮区 */}
      {hasDerived && (
        <div
          className="relative overflow-hidden rounded-2xl p-4"
          style={{
             background:
               'linear-gradient(135deg, hsla(228, 100%, 61%, 0.12) 0%, hsla(200, 80%, 65%, 0.08) 100%)',
             border: '1px solid hsla(228, 100%, 61%, 0.18)',
          }}
        >
          <div className="mb-3 flex items-center gap-2">
            <span className="inline-block size-1.5 rounded-full bg-primary" />
            <span className="text-xs font-semibold text-primary/70">
              {t('record.costStats')}
            </span>
          </div>
          <div className="grid grid-cols-2 gap-y-3 gap-x-4 text-sm sm:grid-cols-2">
            <div className="flex flex-col gap-1">
              <span className="text-[11px] font-medium uppercase tracking-wider text-primary/50">
                 {t('metric.avgOilConsumption')}
              </span>
              {record.shortDistanceSkipped ? (
                <span
                  className="cursor-help text-xl font-bold text-primary/50"
                  title="间隔过短，油耗无参考意义"
                >
                  —
                </span>
              ) : (
                <span className="tabular-nums text-xl font-bold text-primary">
                  {fmt(record.oilConsumption)}{' '}
                   <span className="text-sm font-normal text-primary/60">{t('unit.liter')}</span>
                </span>
              )}
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-[11px] font-medium uppercase tracking-wider text-primary/50">
                 {t('metric.costPerKm')}
              </span>
              {record.shortDistanceSkipped ? (
                <span
                  className="cursor-help text-xl font-bold text-primary/50"
                  title="间隔过短，成本无参考意义"
                >
                  —
                </span>
              ) : (
                <span className="tabular-nums text-xl font-bold text-primary">
                  ¥{fmt(record.costPerKm)}
                </span>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export function RecordTable({
  items,
  loading,
  onDeleted,
}: RecordTableProps) {
  const { t } = useTranslation();
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

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

  return (
    <>
      <div
        className="glass rounded-2xl p-4 sm:p-6"
        style={{ boxShadow: 'var(--glass-shadow)' }}
      >
        {/* 表头区 */}
        <div className="mb-5 flex items-center justify-between sm:mb-6">
          <div className="flex items-center gap-3">
            <div className="flex size-8 items-center justify-center rounded-xl gradient-primary shadow-sm">
              <svg
                className="size-4 text-white"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
              </svg>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-base font-bold text-foreground sm:text-lg">
                 {t('record.title')}
               </span>
               <span className="text-[11px] text-muted-foreground">
                 {t('record.totalCount', { count: items.length })}
               </span>
              </div>
           </div>
           {items.length > 0 && (
             <span className="rounded-full bg-[var(--glass-bg-strong)] px-3.5 py-1.5 text-xs font-semibold text-primary shadow-sm backdrop-blur-sm">
                {t('record.itemCount', { count: items.length })}
             </span>
           )}
         </div>

        {/* 加载态 */}
        {loading && items.length === 0 ? (
          <div className="flex flex-col items-center gap-3 py-16">
            <div className="flex size-12 items-center justify-center rounded-2xl bg-[var(--glass-bg-strong)] shadow-sm">
              <div className="flex gap-1">
                <span
                  className="size-2 animate-bounce rounded-full bg-primary/60"
                  style={{ animationDelay: '0ms' }}
                />
                <span
                  className="size-2 animate-bounce rounded-full bg-primary/60"
                  style={{ animationDelay: '150ms' }}
                />
                <span
                  className="size-2 animate-bounce rounded-full bg-primary/60"
                  style={{ animationDelay: '300ms' }}
                />
              </div>
            </div>
             <div className="text-sm text-muted-foreground">{t('common.loading')}</div>
           </div>
         ) : items.length === 0 ? (
           <div className="flex flex-col items-center gap-3 py-16">
             <div className="flex size-16 items-center justify-center rounded-full bg-[var(--glass-bg-strong)] shadow-sm">
               <svg
                 className="size-8 text-muted-foreground/50"
                 viewBox="0 0 24 24"
                 fill="none"
                 stroke="currentColor"
                 strokeWidth="1.5"
                 strokeLinecap="round"
                 strokeLinejoin="round"
               >
                 <path d="M9 11l3 3L22 4" />
                 <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
               </svg>
             </div>
              <div className="text-sm text-muted-foreground">
                 {t('empty.noRecordsTable')}
               </div>
           </div>
         ) : (
           <div className="flex flex-col gap-4">
             {items.map((record: KoilRecordWithDerived) => (
               <RecordCard
                 key={record.id}
                 record={record}
                 onDelete={setDeleteId}
               />
             ))}
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
     </>
   );
}

export default RecordTable;
