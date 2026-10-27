import { Sparkles, Banknote, Info, AlertCircle } from 'lucide-react';
import { clsx } from 'clsx';

export interface PaymentSummaryCardProps {
  oilAmount: number | null;
  payableAmount: number | null;
  actualAmount: number | null;
  referencePrice: number | null;
  referenceDate?: string | null;
  referenceNote?: string | null;
  isError?: boolean;
  errorMessage?: string | null;
  className?: string;
}

export function PaymentSummaryCard({
  oilAmount,
  payableAmount,
  actualAmount,
  referencePrice,
  referenceDate,
  referenceNote,
  isError = false,
  errorMessage = null,
  className,
}: PaymentSummaryCardProps) {
  const hasPayable = payableAmount !== null && !Number.isNaN(payableAmount) && payableAmount > 0;
  const hasActual = actualAmount !== null && !Number.isNaN(actualAmount) && actualAmount > 0;
  const hasFuel = oilAmount !== null && !Number.isNaN(oilAmount) && oilAmount > 0;
  const hasReference = referencePrice !== null && !Number.isNaN(referencePrice) && referencePrice > 0;

  // 第一行：优惠概览（应付/实付都有时显示）
  const showSavingsRow = hasPayable && hasActual;
  let savingsText = '';
  let savingsColor = '';
  if (showSavingsRow && payableAmount !== null && actualAmount !== null) {
    const diff = payableAmount - actualAmount;
    const pct = payableAmount > 0 ? (diff / payableAmount) * 100 : 0;
    const absDiff = Math.abs(diff).toFixed(2);
    const pctStr = Math.abs(pct).toFixed(1);
    if (diff > 0.005) {
      savingsText = `优惠 ¥${absDiff}（${pctStr}%）`;
      savingsColor = 'text-emerald-600 dark:text-emerald-400';
    } else if (diff < -0.005) {
      savingsText = `多付 ¥${absDiff}`;
      savingsColor = 'text-amber-600 dark:text-amber-400';
    } else {
      savingsText = '无优惠';
      savingsColor = 'text-muted-foreground';
    }
  }

  // 第二行：单价对比（加油量>0 且 实付>0 时显示实际单价）
  const actualUnitPrice = hasFuel && hasActual && oilAmount !== null && actualAmount !== null
    ? actualAmount / oilAmount
    : null;
  const showUnitPriceRow = actualUnitPrice !== null && actualUnitPrice > 0 && !Number.isNaN(actualUnitPrice);

  let unitPriceDiffText = '';
  let unitPriceDiffColor = '';
  if (showUnitPriceRow && hasReference && actualUnitPrice !== null && referencePrice !== null) {
    const diff = referencePrice - actualUnitPrice;
    const pct = referencePrice > 0 ? (diff / referencePrice) * 100 : 0;
    const absDiff = Math.abs(diff).toFixed(2);
    const pctStr = Math.abs(pct).toFixed(1);
    if (diff > 0.005) {
      unitPriceDiffText = `省 ¥${absDiff}/L（${pctStr}%）`;
      unitPriceDiffColor = 'text-emerald-600 dark:text-emerald-400';
    } else if (diff < -0.005) {
      unitPriceDiffText = `贵 ¥${absDiff}/L（${pctStr}%）`;
      unitPriceDiffColor = 'text-amber-600 dark:text-amber-400';
    } else {
      unitPriceDiffText = '与参考价一致';
      unitPriceDiffColor = 'text-muted-foreground';
    }
  }

  // 第三行：referenceNote
  const showNoteRow = !!referenceNote;

  // 错误行
  const showErrorRow = isError && !!errorMessage;

  if (!showSavingsRow && !showUnitPriceRow && !showNoteRow && !showErrorRow) {
    return null;
  }

  const baseClass = isError
    ? 'bg-destructive/10 ring-1 ring-inset ring-destructive/20'
    : 'bg-muted-foreground/5 ring-1 ring-inset ring-muted-foreground/10';

  return (
    <div
      className={clsx(
        'rounded-2xl px-4 py-3 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] animate-in fade-in slide-in-from-top-1 duration-300',
        baseClass,
        className,
      )}
    >
      <div className="flex flex-col gap-2">
        {showErrorRow && (
          <div className="flex items-start gap-2 text-[12px] text-destructive">
            <AlertCircle className="mt-[1px] size-3.5 shrink-0" />
            <span className="leading-snug font-medium">{errorMessage}</span>
          </div>
        )}

        {showSavingsRow && (
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 text-[12px] text-muted-foreground">
              <Sparkles className="size-3.5 shrink-0 text-primary/70" />
              <span className="tabular-nums">
                应付 ¥{payableAmount!.toFixed(2)} · 实付 ¥{actualAmount!.toFixed(2)}
              </span>
            </div>
            <span className={clsx('text-sm font-semibold tabular-nums', savingsColor)}>
              {savingsText}
            </span>
          </div>
        )}

        {showUnitPriceRow && actualUnitPrice !== null && (
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 text-[12px] text-muted-foreground">
              <Banknote className="size-3.5 shrink-0 text-primary/70" />
              <span className="tabular-nums">
                实际单价 <span className="font-medium text-foreground">¥{actualUnitPrice.toFixed(2)}/L</span>
                {hasReference && (
                  <>
                    <span className="mx-1 text-muted-foreground/40">·</span>
                    参考价 ¥{referencePrice!.toFixed(2)}/L
                  </>
                )}
              </span>
            </div>
            {hasReference && unitPriceDiffText && (
              <span className={clsx('text-[12px] font-medium tabular-nums', unitPriceDiffColor)}>
                {unitPriceDiffText}
              </span>
            )}
          </div>
        )}

        {showNoteRow && (
          <div className="flex items-start gap-1.5 text-[11px] text-muted-foreground">
            <Info className="mt-[1px] size-3 shrink-0 opacity-70" />
            <span className="leading-snug">{referenceNote}</span>
          </div>
        )}
      </div>
    </div>
  );
}

export default PaymentSummaryCard;
