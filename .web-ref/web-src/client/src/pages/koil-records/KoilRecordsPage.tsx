import { useState, useCallback, useRef, useEffect, useLayoutEffect, useMemo, lazy, Suspense } from 'react';
import { useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { Home, Settings, ArrowUpToLine } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

import { SummaryCards, AbnormalAlertCard } from './SummaryCards';
import { RecordForm } from './RecordForm';
import { RecordList } from './RecordList';
import { ErrorBoundary } from 'react-error-boundary';
import { getKoilRecords } from '@client/src/api/koil-records';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { useTranslation } from '@client/src/hooks/useTranslation';
import { BounceScroll } from '@client/src/components/ui/bounce-scroll';
import { StickyHeader } from '@client/src/components/ui/sticky-header';
import { AppTabNav } from '@client/src/components/ui/app-tab-nav';

import type {
  KoilSummary as KoilSummaryType,
  KoilRecordWithDerived,
} from '@shared/api.interface';
import { getKoilItem, setKoilItem } from '@client/src/utils/koil-storage';

const TrendCharts = lazy(() => import('./TrendCharts').then((m) => ({ default: m.TrendCharts })));
const SettingsPanel = lazy(() => import('./SettingsPanel').then((m) => ({ default: m.SettingsPanel })));

type TabKey = 'add' | 'overview' | 'settings';

const TABS_ICONS: { key: TabKey; icon: typeof Home }[] = [
  { key: 'add', icon: Home },
  { key: 'overview', icon: Home },
  { key: 'settings', icon: Settings },
];

export function KoilRecordsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const getTabFromUrl = (): TabKey => {
    const t = searchParams.get('tab');
    if (t === 'overview' || t === 'summary' || t === 'history') return 'overview';
    if (t === 'settings') return 'settings';
    return 'add';
  };

  const [summary, setSummary] = useState<KoilSummaryType | null>(null);
  const [initialLoaded, setInitialLoaded] = useState(false);
  const [summaryItems, setSummaryItems] = useState<KoilRecordWithDerived[]>([]);
  const [summaryLoaded, setSummaryLoaded] = useState(false);
  const [summaryRange, setSummaryRange] = useState<string>(() => {
    if (typeof window === 'undefined') return 'year';
    try {
      const saved = getKoilItem('summary-range');
      if (saved === 'all' || saved === 'year' || saved === '6m' || saved === '3m') {
        return saved;
      }
    } catch {
      // ignore
    }
    return 'year';
  });

  const handleRangeChange = useCallback((newRange: string) => {
    setSummaryRange(newRange);
    try {
      setKoilItem('summary-range', newRange);
    } catch {
      // ignore
    }
  }, []);
  const [refreshKey, setRefreshKey] = useState(0);
  const [activeTab, setActiveTab] = useState<TabKey>(() => getTabFromUrl());
  const [dragX, setDragX] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [showBackToTop, setShowBackToTop] = useState(false);
  const [navVisible, setNavVisible] = useState(true);
  const [containerWidth, setContainerWidth] = useState(0);
  const [headerCollapsed, setHeaderCollapsed] = useState(false);
  const handleHeaderCollapseChange = useCallback((collapsed: boolean) => {
    setHeaderCollapsed(collapsed);
  }, []);
  const [backToTopRight, setBackToTopRight] = useState<string>('1rem');
  const containerRef = useRef<HTMLDivElement>(null);
  const pageRef = useRef<HTMLDivElement>(null);
  const historyScrollRef = useRef<HTMLDivElement>(null);
  const formScrollRef = useRef<HTMLDivElement>(null);
  const settingsScrollRef = useRef<HTMLDivElement>(null);
  const startXRef = useRef(0);
  const startYRef = useRef(0);
  const gestureLockRef = useRef<'none' | 'horizontal' | 'vertical'>('none');
  const updateTabInUrl = useCallback(
    (key: TabKey) => {
      const params = new URLSearchParams(location.search);
      params.set('tab', key);
      navigate(`?${params.toString()}`, { replace: false });
    },
    [location.search, navigate],
  );

  useEffect(() => {
    const urlTab = getTabFromUrl();
    if (urlTab !== activeTab) {
      setActiveTab(urlTab);
      setShowBackToTop(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    document.title = `${t('app.name')} · ${t('app.tagline')}`;
  }, [t]);

  const loadSummaryData = useCallback(async (force = false) => {
    try {
      setSummaryLoaded(false);
      const data = await getKoilRecords({ pageSize: 999 });
      setSummaryItems(data.items);
    } catch (error) {
      logger.error('加载汇总数据失败', error);
    } finally {
      setSummaryLoaded(true);
    }
  }, []);

  const handleRefresh = useCallback(() => {
    setInitialLoaded(false);
    setRefreshKey((k) => k + 1);
    void loadSummaryData(true);
  }, [loadSummaryData]);

  const getAbnormalThreshold = (): number => {
    if (typeof window === 'undefined') return 30;
    try {
      const raw = getKoilItem('abnormal-threshold');
      if (raw == null) return 30;
      const n = Number(raw);
      if (Number.isFinite(n) && n >= 5 && n <= 100) return n;
    } catch {
      // ignore
    }
    return 30;
  };

  const [abnormalThreshold, setAbnormalThreshold] = useState<number>(() => getAbnormalThreshold());

  const TABS = useMemo<{ key: TabKey; label: string; icon: typeof Home }[]>(() => [
    { key: 'add', label: t('nav.add'), icon: Home },
    { key: 'overview', label: t('nav.overview'), icon: Home },
    { key: 'settings', label: t('nav.settings'), icon: Settings },
  ], [t]);

  useEffect(() => {
    const handler = () => setAbnormalThreshold(getAbnormalThreshold());
    window.addEventListener('koil-settings-updated', handler);
    return () => window.removeEventListener('koil-settings-updated', handler);
  }, []);

  const processedSummaryItems = useMemo(() => {
    if (summaryItems.length === 0) return summaryItems;
    const consumptions = summaryItems
      .map((r) => r.oilConsumption)
      .filter((v): v is number => v != null && v > 0);
    const avgConsumption =
      consumptions.length > 0
        ? consumptions.reduce((sum, v) => sum + v, 0) / consumptions.length
        : 0;
    if (avgConsumption <= 0) return summaryItems;
    const highThreshold = avgConsumption * (1 + abnormalThreshold / 100);
    const lowThreshold = avgConsumption * (1 - abnormalThreshold / 100);
    return summaryItems.map((r) => {
      if (r.oilConsumption == null) return r;
      let level: 'high' | 'low' | 'normal' | null = 'normal';
      let abnormal = false;
      if (r.oilConsumption > highThreshold) {
        level = 'high';
        abnormal = true;
      } else if (r.oilConsumption < lowThreshold) {
        level = 'low';
        abnormal = true;
      }
      if (abnormal === r.isAbnormalOilConsumption && level === r.oilConsumptionLevel) return r;
      return { ...r, isAbnormalOilConsumption: abnormal, oilConsumptionLevel: level };
    });
  }, [summaryItems, abnormalThreshold]);

  const currentRangeItems = useMemo(() => {
    const rangeKey = (summaryRange as 'all' | 'year' | '6m' | '3m') ?? 'all';
    if (rangeKey === 'all') return processedSummaryItems;
    const now = new Date();
    let startMs: number;
    if (rangeKey === 'year') {
      startMs = new Date(now.getFullYear(), 0, 1).getTime();
    } else if (rangeKey === '6m') {
      startMs = now.getTime() - 180 * 86400000;
    } else {
      startMs = now.getTime() - 90 * 86400000;
    }
    return processedSummaryItems.filter((r) => new Date(r.reoilDate).getTime() >= startMs);
  }, [processedSummaryItems, summaryRange]);

  const abnormalCountInRange = useMemo(() =>
    currentRangeItems.filter((r) => r.isAbnormalOilConsumption).length
  , [currentRangeItems]);

  const avgConsumptionInRange = useMemo(() => {
    const valid = currentRangeItems
      .filter((r) => r.oilConsumption != null && !r.shortDistanceSkipped && r.distance != null && r.distance >= 100)
      .map((r) => r.oilConsumption as number);
    if (valid.length === 0) return 0;
    return valid.reduce((s, v) => s + v, 0) / valid.length;
  }, [currentRangeItems]);

  const rangeLabel = (() => {
    switch (summaryRange) {
      case 'all': return t('range.all');
      case 'year': return t('range.thisYear');
      case '6m': return t('range.last6Months');
      case '3m': return t('range.last3Months');
      default: return t('range.all');
    }
  })();

  useEffect(() => {
    void loadSummaryData();
  }, [loadSummaryData]);

  useLayoutEffect(() => {
    const updateLayout = () => {
      if (!pageRef.current) return;
      const pageRect = pageRef.current.getBoundingClientRect();
      const rightGap = window.innerWidth - pageRect.right;
      const baseGap = Math.max(rightGap, 16);
      const isSm = window.innerWidth >= 640;
      setBackToTopRight(`${isSm ? baseGap + 16 : baseGap}px`);
    };
    const updateContainerWidth = () => {
      if (containerRef.current) {
        setContainerWidth(containerRef.current.offsetWidth);
      }
    };
    updateLayout();
    updateContainerWidth();
    window.addEventListener('resize', updateLayout);
    window.addEventListener('resize', updateContainerWidth);
    window.addEventListener('scroll', updateLayout, true);
    const ro = new ResizeObserver(updateContainerWidth);
    if (containerRef.current) ro.observe(containerRef.current);
    const pageRo = new ResizeObserver(updateLayout);
    if (pageRef.current) pageRo.observe(pageRef.current);
    return () => {
      window.removeEventListener('resize', updateLayout);
      window.removeEventListener('resize', updateContainerWidth);
      window.removeEventListener('scroll', updateLayout, true);
      ro.disconnect();
      pageRo.disconnect();
    };
  }, []);

  useEffect(() => {
    if (!window.visualViewport) return;
    const handleResize = () => {
      const vh = window.visualViewport?.height ?? window.innerHeight;
      const fullVh = window.innerHeight;
      const diff = fullVh - vh;
      setNavVisible(diff < 150);
    };
    window.visualViewport.addEventListener('resize', handleResize);
    return () => window.visualViewport?.removeEventListener('resize', handleResize);
  }, []);

  const handleOverviewScroll = (e: React.UIEvent<HTMLDivElement>) => {
    setShowBackToTop(e.currentTarget.scrollTop > 200);
  };

  const handleBackToTop = () => {
    if (!historyScrollRef.current) return;
    historyScrollRef.current.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handleTabClick = (key: TabKey) => {
    if (key === activeTab) return;
    setActiveTab(key);
    setShowBackToTop(false);
    updateTabInUrl(key);
    if (key === 'overview' && historyScrollRef.current) {
      historyScrollRef.current.scrollTop = 0;
    }
  };

  const handleViewRecord = useCallback(() => {
    if (activeTab === 'overview') {
      if (historyScrollRef.current) {
        historyScrollRef.current.scrollTo({ top: 0, behavior: 'smooth' });
      }
      return;
    }
    handleTabClick('overview');
  }, [activeTab, handleTabClick]);

  const handleDragStart = (e: React.PointerEvent<HTMLDivElement>) => {
    if (e.pointerType === 'touch') {
      startXRef.current = e.clientX;
      startYRef.current = e.clientY;
      gestureLockRef.current = 'none';
      return;
    }
    startXRef.current = e.clientX;
    startYRef.current = e.clientY;
    setIsDragging(true);
    gestureLockRef.current = 'horizontal';
  };

  const handleDragMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const dx = e.clientX - startXRef.current;
    const dy = e.clientY - startYRef.current;

    if (e.pointerType === 'touch' && gestureLockRef.current === 'none') {
      const absDx = Math.abs(dx);
      const absDy = Math.abs(dy);
      if (absDx < 8 && absDy < 8) return;
      if (absDy > absDx) {
        gestureLockRef.current = 'vertical';
        return;
      }
      gestureLockRef.current = 'horizontal';
      setIsDragging(true);
    }

    if (!isDragging || gestureLockRef.current !== 'horizontal') return;
    const diff = dx;
    const currentIdx = TABS.findIndex((t) => t.key === activeTab);
    if (currentIdx === 0 && diff > 0) {
      setDragX(diff * 0.3);
    } else if (currentIdx === TABS.length - 1 && diff < 0) {
      setDragX(diff * 0.3);
    } else {
      setDragX(diff);
    }
  };

  const handleDragEnd = () => {
    const wasDragging = isDragging;
    const wasHorizontal = gestureLockRef.current === 'horizontal';
    setIsDragging(false);
    gestureLockRef.current = 'none';
    if (!wasDragging) return;
    const threshold = containerWidth * 0.2;
    const currentIdx = TABS.findIndex((t) => t.key === activeTab);

    let willSwitch = false;
    if (dragX < -threshold && currentIdx < TABS.length - 1) {
      const nextKey = TABS[currentIdx + 1].key;
      setActiveTab(nextKey);
      setShowBackToTop(false);
      updateTabInUrl(nextKey);
      willSwitch = true;
    } else if (dragX > threshold && currentIdx > 0) {
      const prevKey = TABS[currentIdx - 1].key;
      setActiveTab(prevKey);
      setShowBackToTop(false);
      updateTabInUrl(prevKey);
      willSwitch = true;
    }
    setDragX(0);

  };

  const activeIdx = TABS.findIndex((t) => t.key === activeTab);
  const basePx = -(activeIdx * containerWidth);
  const dragPx = isDragging ? dragX : 0;
  const translatePx = containerWidth > 0 ? basePx + dragPx : 0;

   const currentTabTitle = activeTab === 'add' ? t('nav.add') : activeTab === 'overview' ? t('nav.overview') : t('nav.settings');

   const currentScrollRef = activeTab === 'add'
     ? formScrollRef
     : activeTab === 'overview'
     ? historyScrollRef
     : settingsScrollRef;

   return (
    <div
      ref={pageRef}
      className="relative flex w-full flex-col overflow-hidden bg-background"
      style={{ height: '100dvh' }}
    >
      {/* 背景光晕 */}
      <div className="pointer-events-none absolute inset-0 -z-10 bg-glow opacity-80" />

      {/* 宽屏侧边栏导航（absolute定位，与StickyHeader同层级，保证毛玻璃真机生效） */}
      <div className="pointer-events-none absolute left-3 top-0 bottom-0 z-30 hidden items-center lg:flex">
        <AppTabNav
          tabs={TABS.map((t) => ({ ...t, key: t.key as string }))}
          activeKey={activeTab}
          onChange={(k) => handleTabClick(k as TabKey)}
          variant="side-rail"
        />
      </div>

       {/* 主内容区 */}
       <div
          ref={containerRef}
          className="relative flex-1 min-h-0 overflow-hidden touch-pan-y bg-background"
          onPointerDown={handleDragStart}
          onPointerMove={handleDragMove}
          onPointerUp={handleDragEnd}
         onPointerCancel={handleDragEnd}
         onPointerLeave={handleDragEnd}
       >
         {/* 固定顶部栏（不跟随滚动回弹） */}
           <div
             data-sticky-header-bar
              className={`sticky-header-bar pointer-events-none absolute inset-x-0 top-0 z-20 lg:pl-24${headerCollapsed ? ' sticky-header-bar--hairline' : ''}`}
           >
             <div className="mx-auto w-full max-w-[1100px] px-7">
              <StickyHeader
                 key={activeTab}
                 title={currentTabTitle}
                 scrollRef={currentScrollRef}
                 onMaskChange={handleHeaderCollapseChange}
               />
           </div>
         </div>

         <div
          className="flex h-full bg-background"
          data-koil-tab-active={activeTab}
          style={{
            transform: `translateX(${translatePx}px)`,
            transition: isDragging
              ? 'none'
              : 'transform 400ms cubic-bezier(0.22, 1, 0.36, 1)',
             width: `${containerWidth * 3}px`,
           }}
         >
             {/* 录入页 */}
             <BounceScroll
                scrollRef={formScrollRef}
                className="shrink-0 px-3 pb-40 sm:px-8 sm:pb-44 lg:pb-10 lg:pl-24"
                style={{ width: `${containerWidth}px`, paddingTop: 'var(--sticky-header-height, 56px)' }}
              >
                <div className="mx-auto w-full max-w-[1100px]">
                  <RecordForm onSuccess={handleRefresh} onViewRecord={handleViewRecord} />
                </div>
              </BounceScroll>

            {/* 数据总览页：汇总 + 趋势图 + 历史列表（滚动连续） */}
             <BounceScroll
               scrollRef={historyScrollRef}
               onScroll={handleOverviewScroll}
                 className="shrink-0 px-3 pb-40 sm:px-8 sm:pb-44 lg:pl-24"
               style={{ width: `${containerWidth}px`, paddingTop: 'var(--sticky-header-height, 56px)' }}
            >
              <div className="mx-auto w-full max-w-[1100px]">
                  <SummaryCards items={processedSummaryItems} loading={!summaryLoaded} range={summaryRange} onRangeChange={handleRangeChange} abnormalThreshold={abnormalThreshold} hideAbnormalCard />
                 <div className="mt-6 sm:mt-8">
                   <ErrorBoundary
                     fallback={
                        <div className="glass-soft flex h-[320px] flex-col items-center justify-center rounded-2xl text-center sm:h-[380px]">
                          <p className="text-sm font-medium text-foreground-secondary">{t('empty.trendLoadFailed')}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{t('empty.trendRetry')}</p>
                        </div>
                     }
                   >
                     <Suspense fallback={<div className="glass-soft h-[320px] animate-pulse rounded-2xl sm:h-[380px]" />}>
                       <TrendCharts items={processedSummaryItems} range={summaryRange} loading={!summaryLoaded} />
                     </Suspense>
                   </ErrorBoundary>
                 </div>
                 {summaryLoaded && processedSummaryItems.length > 0 && (
                   <div className="mt-6 sm:mt-8">
                     <AbnormalAlertCard
                       count={abnormalCountInRange}
                       threshold={abnormalThreshold}
                       items={currentRangeItems}
                       avgConsumption={avgConsumptionInRange}
                       rangeLabel={rangeLabel}
                       recordCount={currentRangeItems.length}
                     />
                   </div>
                 )}
                <div className="mt-6 sm:mt-8">
                  <RecordList
                    key={refreshKey}
                    refreshKey={refreshKey}
                    onDeleted={handleRefresh}
                    onEdited={handleRefresh}
                    onSummaryUpdate={setSummary}
                    onInitialLoad={() => setInitialLoaded(true)}
                   />
                </div>
              </div>
           </BounceScroll>

            {/* 设置页 */}
            <BounceScroll
               scrollRef={settingsScrollRef}
               className="shrink-0 px-3 pb-40 sm:px-8 sm:pb-44 lg:pb-10 lg:pl-24"
               style={{ width: `${containerWidth}px`, paddingTop: 'var(--sticky-header-height, 56px)' }}
             >
                <div className="mx-auto w-full max-w-[1100px]">
                 <Suspense fallback={<div className="glass-soft h-[400px] animate-pulse rounded-2xl" />}>
                  <SettingsPanel onDataChanged={handleRefresh} activeTab={activeTab} />
                </Suspense>
              </div>
           </BounceScroll>
         </div>
      </div>

      {/* 返回顶部按钮 */}
      <AnimatePresence>
        {activeTab === 'overview' && showBackToTop && (
          <motion.button
            initial={{ opacity: 0, y: 14, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 14, scale: 0.9 }}
            transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
            onClick={handleBackToTop}
             aria-label={t('settings.backToLatest')}
               className="absolute bottom-24 z-30 flex size-12 items-center justify-center rounded-full gradient-primary text-white shadow-lg shadow-black/30 transition-all duration-300 sm:bottom-28"
              style={{ right: backToTopRight }}
           >
             <ArrowUpToLine className="size-5" />
           </motion.button>
         )}
       </AnimatePresence>

       {/* 窄屏底部悬浮胶囊导航（absolute定位，与StickyHeader同层级，零transform保证毛玻璃真机生效） */}
       <AppTabNav
         tabs={TABS.map((t) => ({ ...t, key: t.key as string }))}
         activeKey={activeTab}
         onChange={(k) => handleTabClick(k as TabKey)}
         variant="bottom-bar"
         className={`absolute left-0 right-0 z-40 mx-auto w-fit transition-opacity duration-300 ease-out lg:hidden ${
           navVisible ? 'opacity-100' : 'opacity-0'
         }`}
         style={{ bottom: 'calc(16px + env(safe-area-inset-bottom, 0px))' }}
       />
     </div>
   );
}

export default KoilRecordsPage;
