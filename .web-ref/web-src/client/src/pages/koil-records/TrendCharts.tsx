import { useMemo } from 'react';
import { motion } from 'framer-motion';
import ReactECharts from 'echarts-for-react';
import type { EChartsOption } from 'echarts';
import type { TopLevelFormatterParams } from 'echarts/types/dist/shared';
import { TrendingUp } from 'lucide-react';

import { useTheme } from '@client/src/hooks/useTheme';
import { useTranslation } from '@client/src/hooks/useTranslation';
import type { KoilRecordWithDerived } from '@shared/api.interface';

type RangeKey = 'all' | 'year' | '6m' | '3m';

const CHART_PRIMARY = '#0071E3';
const CHART_SECONDARY = '#5EB8E6';

const TOOLTIP_BG_LIGHT = 'rgba(255, 255, 255, 0.95)';
const TOOLTIP_BORDER_LIGHT = 'rgba(0, 0, 0, 0.05)';
const TOOLTIP_TEXT_LIGHT = '#2a2520';
const TOOLTIP_MUTED_LIGHT = '#8b94ad';
const GRID_LINE_LIGHT = 'rgba(0, 0, 0, 0.05)';
const AXIS_TEXT_LIGHT = '#8b94ad';
const AXIS_LINE_LIGHT = 'ececec';

const TOOLTIP_BG_DARK = 'rgba(28, 32, 48, 0.92)';
const TOOLTIP_BORDER_DARK = 'rgba(255, 255, 255, 0.10)';
const TOOLTIP_TEXT_DARK = '#e6e8ef';
const TOOLTIP_MUTED_DARK = '#8b94ad';
const GRID_LINE_DARK = 'rgba(255, 255, 255, 0.08)';
const AXIS_TEXT_DARK = '#8b94ad';
const AXIS_LINE_DARK = 'rgba(255, 255, 255, 0.08)';

interface TrendChartsProps {
  items: KoilRecordWithDerived[];
  range: string;
  loading: boolean;
}

function EmptyState({ message }: { message: string }) {
  const { t } = useTranslation();
  return (
    <div className="glass-soft flex h-full min-h-[280px] flex-col items-center justify-center rounded-2xl text-center sm:min-h-[340px]">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-primary-soft">
        <TrendingUp className="h-7 w-7 text-primary" />
      </div>
      <p className="text-sm font-medium text-foreground-secondary sm:text-base">
        {message}
      </p>
      <p className="mt-1.5 text-xs text-muted-foreground sm:text-sm">
        {t('empty.noTrendData')}
      </p>
    </div>
  );
}

function SkeletonChart() {
  return (
    <div className="flex h-full min-h-[280px] flex-col gap-4 sm:min-h-[340px]">
      <motion.div
        className="h-4 w-32 rounded-md bg-surface-3/50"
        animate={{ opacity: [0.4, 0.8, 0.4] }}
        transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
      />
      <div className="glass-soft relative flex-1 overflow-hidden rounded-2xl">
        <div className="absolute inset-x-5 top-8 bottom-12 flex flex-col justify-between">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-px w-full bg-border/60" />
          ))}
        </div>
        <svg
          className="absolute inset-0 h-full w-full"
          viewBox="0 0 400 200"
          preserveAspectRatio="none"
        >
          <defs>
             <linearGradient id="skeletonGrad2" x1="0" y1="0" x2="1" y2="0">
               <stop offset="0%" stopColor="rgba(55, 101, 255, 0)" />
               <stop offset="50%" stopColor="rgba(55, 101, 255, 0.5)" />
               <stop offset="100%" stopColor="rgba(55, 101, 255, 0)" />
             </linearGradient>
          </defs>
          <path
            d="M0,140 C80,100 120,160 200,120 C280,80 320,140 400,100 L400,200 L0,200 Z"
            fill="url(#skeletonGrad2)"
            opacity="0.25"
          >
            <animate
              attributeName="opacity"
              values="0.15;0.4;0.15"
              dur="2s"
              repeatCount="indefinite"
            />
          </path>
          <path
            d="M0,140 C80,100 120,160 200,120 C280,80 320,140 400,100"
            fill="none"
            stroke="url(#skeletonGrad2)"
            strokeWidth="2.5"
          >
            <animate
              attributeName="stroke-dasharray"
              values="0 800;800 0"
              dur="2s"
              repeatCount="indefinite"
            />
          </path>
        </svg>
      </div>
    </div>
  );
}

function formatDateMMDD(dateStr: string): string {
  const date = new Date(dateStr);
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${mm}/${dd}`;
}

export function TrendCharts({
  items,
  range,
  loading,
}: TrendChartsProps) {
  const { isDark } = useTheme();
  const { t } = useTranslation();

  const tooltipBg = isDark ? TOOLTIP_BG_DARK : TOOLTIP_BG_LIGHT;
  const tooltipBorder = isDark ? TOOLTIP_BORDER_DARK : TOOLTIP_BORDER_LIGHT;
  const tooltipText = isDark ? TOOLTIP_TEXT_DARK : TOOLTIP_TEXT_LIGHT;
  const tooltipMuted = isDark ? TOOLTIP_MUTED_DARK : TOOLTIP_MUTED_LIGHT;
  const gridLine = isDark ? GRID_LINE_DARK : GRID_LINE_LIGHT;
  const axisText = isDark ? AXIS_TEXT_DARK : AXIS_TEXT_LIGHT;
  const axisLine = isDark ? AXIS_LINE_DARK : AXIS_LINE_LIGHT;
  const itemBorder = isDark ? '#1a1a1c' : '#fff';
  const tooltipShadow = isDark
    ? 'box-shadow: 0 8px 24px -8px rgba(0,0,0,0.4); backdrop-filter: blur(12px);'
    : 'box-shadow: 0 8px 24px -8px rgba(0,0,0,0.08); backdrop-filter: blur(12px);';

  const filteredItems = useMemo(() => {
    const r = range as RangeKey;
    if (r === 'all') return items;
    const now = new Date();
    let start: Date;
    switch (r) {
      case 'year':
        start = new Date(now.getFullYear(), 0, 1);
        break;
      case '6m':
        start = new Date(now);
        start.setMonth(start.getMonth() - 6);
        break;
      case '3m':
        start = new Date(now);
        start.setMonth(start.getMonth() - 3);
        break;
      default:
        return items;
    }
    return items.filter((r) => new Date(r.reoilDate) >= start);
  }, [items, range]);

  const sortedItems = useMemo(() => {
    return [...filteredItems].sort(
      (a, b) => new Date(a.reoilDate).getTime() - new Date(b.reoilDate).getTime(),
    );
  }, [filteredItems]);

  const trendItems = useMemo(() => {
    return sortedItems.filter(
      (r) =>
        r.oilConsumption !== null &&
        r.oilConsumption !== undefined &&
        r.costPerKm !== null &&
        r.costPerKm !== undefined,
    );
  }, [sortedItems]);

  const dualAxisOption: EChartsOption = useMemo(() => {
    const xData = trendItems.map((r) => formatDateMMDD(r.reoilDate));
    const fuelData = trendItems.map((r) => r.oilConsumption);
    const costData = trendItems.map((r) => r.costPerKm);

    const fuelName = t('metric.avgOilConsumption');
    const costName = t('trend.tooltipCostPerKm');

    return {
      tooltip: {
        trigger: 'axis',
        backgroundColor: tooltipBg,
        borderColor: tooltipBorder,
        borderWidth: 1,
        padding: [10, 14],
        textStyle: {
          color: tooltipText,
          fontSize: 12,
        },
        extraCssText: tooltipShadow,
        axisPointer: {
          type: 'line',
           lineStyle: {
             color: 'rgba(55, 101, 255, 0.4)',
             type: 'dashed',
           },
        },
        formatter: (params: TopLevelFormatterParams) => {
          const list = Array.isArray(params) ? params : [params];
          if (list.length === 0) return '';
          const dateLabel = list[0].name ?? '';
          const fuelItem = list.find((p) => p.seriesName === fuelName);
          const costItem = list.find((p) => p.seriesName === costName);

          const fuelVal = fuelItem?.value as number | undefined;
          const costVal = costItem?.value as number | undefined;

          let html = `<div style="font-size:11px;letter-spacing:0.04em;text-transform:uppercase;color:${tooltipMuted};margin-bottom:8px;">${dateLabel}</div>`;

          if (fuelVal !== undefined) {
            html += `<div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${CHART_PRIMARY};"></span>
              <span style="color:${tooltipText};font-size:12px;font-weight:500;">${fuelName}</span>
              <span style="margin-left:auto;color:${CHART_PRIMARY};font-size:14px;font-weight:700;">${Number(fuelVal).toFixed(2)}</span>
              <span style="color:${tooltipMuted};font-size:10px;">${t('unit.lPer100km')}</span>
            </div>`;
          }
          if (costVal !== undefined) {
            html += `<div style="display:flex;align-items:center;gap:8px;">
              <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${CHART_SECONDARY};"></span>
              <span style="color:${tooltipText};font-size:12px;font-weight:500;">${costName}</span>
              <span style="margin-left:auto;color:${CHART_SECONDARY};font-size:14px;font-weight:700;">${Number(costVal).toFixed(2)}</span>
              <span style="color:${tooltipMuted};font-size:10px;">${t('unit.yuanPerKm')}</span>
            </div>`;
          }
          return html;
        },
      },
      legend: {
        type: 'scroll',
        bottom: 0,
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
        textStyle: {
          fontSize: 11,
          color: axisText,
        },
      },
      grid: {
        left: '3%',
        right: '5%',
        bottom: '20%',
        top: '6%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: xData,
        axisLabel: {
          fontSize: 11,
          color: axisText,
        },
        axisLine: {
          lineStyle: { color: axisLine },
        },
        axisTick: { show: false },
      },
      yAxis: [
        {
          type: 'value',
          axisLabel: {
            fontSize: 11,
            color: axisText,
            formatter: (value: number) => value.toFixed(1),
          },
          splitLine: {
            lineStyle: { color: gridLine, type: 'dashed' },
          },
          axisLine: { show: false },
          axisTick: { show: false },
        },
        {
          type: 'value',
          axisLabel: {
            fontSize: 11,
            color: axisText,
            formatter: (value: number) => value.toFixed(2),
          },
          splitLine: { show: false },
          axisLine: { show: false },
          axisTick: { show: false },
        },
      ],
      series: [
        {
          name: fuelName,
          type: 'line',
          yAxisIndex: 0,
          data: fuelData,
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: {
            width: 3,
            color: CHART_PRIMARY,
          },
          itemStyle: {
            color: CHART_PRIMARY,
            borderColor: itemBorder,
            borderWidth: 3,
          },
          emphasis: {
            scale: true,
          },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                 {
                   offset: 0,
                   color: isDark
                     ? 'rgba(55, 101, 255, 0.20)'
                     : 'rgba(55, 101, 255, 0.28)',
                 },
                 {
                   offset: 1,
                   color: isDark
                     ? 'rgba(55, 101, 255, 0.02)'
                     : 'rgba(55, 101, 255, 0.04)',
                 },
              ],
            },
          },
        },
        {
          name: costName,
          type: 'line',
          yAxisIndex: 1,
          data: costData,
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: {
            width: 3,
            color: CHART_SECONDARY,
          },
          itemStyle: {
            color: CHART_SECONDARY,
            borderColor: itemBorder,
            borderWidth: 3,
          },
          emphasis: {
            scale: true,
          },
        },
      ],
    };
  }, [
    trendItems,
    isDark,
    tooltipBg,
    tooltipBorder,
    tooltipText,
    tooltipMuted,
    tooltipShadow,
    axisText,
    axisLine,
    gridLine,
    itemBorder,
    t,
  ]);

  const renderChartContent = () => {
    if (trendItems.length >= 2) {
      return (
        <ReactECharts
          option={dualAxisOption}
          className="h-full min-h-[280px] w-full sm:min-h-[340px] lg:min-h-[380px]"
          opts={{ renderer: 'svg' }}
        />
      );
    }
    return <EmptyState message={t('empty.noOilConsumptionTrend')} />;
  };

  return (
    <div className="glass rounded-2xl p-4 transition-[background-color,border-color,box-shadow] duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]">
      <div className="mb-5 flex items-center gap-3 sm:mb-6">
        <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-primary-soft">
          <TrendingUp className="h-5 w-5 text-primary" />
        </span>
        <span className="text-lg font-semibold text-foreground sm:text-xl">
          {t('trend.title')}
        </span>
      </div>

      {loading ? (
        <div className="w-full">
          <SkeletonChart />
        </div>
      ) : (
        <div className="relative w-full overflow-hidden">
          <motion.div
            key={range}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}
            className="w-full"
          >
            {renderChartContent()}
          </motion.div>
        </div>
      )}
    </div>
  );
}

export default TrendCharts;
