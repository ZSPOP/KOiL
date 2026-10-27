import React, { useState, useEffect, useCallback } from 'react';
import {
  Sun,
  Moon,
  Monitor,
  Globe,
  Settings,
  AlertTriangle,
  Fuel,
  Download,
  Upload,
  FileJson,
  Info,
  Check,
  X,
  Cloud,
  Database,
  RefreshCw,
  MapPin,
  Gauge,
  Calendar,
  ChevronRight,
  ChevronUp,
  ChevronDown,
  Plus as PlusIcon,
  Minus as MinusIcon,
  ChevronsUpDown,
  TrendingUp,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { useTheme, type ThemeMode } from '@client/src/hooks/useTheme';
import { useTranslation, type LanguageMode } from '@client/src/hooks/useTranslation';
import { Button } from '@client/src/components/ui/button';
import { Popover, PopoverTrigger, PopoverContent, PopoverClose } from '@client/src/components/ui/popover';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@client/src/components/ui/select';
import { exportKoilRecordsCsv, exportBackupJson, getOilPriceDbStatus, updateOilPriceDb, setOilPriceDbAutoUpdate, getOilPricePending, acceptOilPricePending, ignoreOilPricePending, getPriceDeviationThreshold, setPriceDeviationThreshold, exportOilPriceHistoryCsv } from '@client/src/api/koil-records';
import type { KoilPricePendingItem } from '@shared/api.interface';
import { useLocationPermission } from '@client/src/hooks/use-location-permission';
import { usePriceAlert } from '@client/src/hooks/usePriceAlert';
import { useSearchParams } from 'react-router-dom';
import { ImportDialog } from './ImportDialog';

interface SettingsPanelProps {
  onDataChanged?: () => void;
  activeTab?: string;
}

const PROVINCES = [
  '北京', '天津', '河北', '山西', '内蒙古', '辽宁', '吉林', '黑龙江',
  '上海', '江苏', '浙江', '安徽', '福建', '江西', '山东', '河南',
  '湖北', '湖南', '广东', '广西', '海南', '重庆', '四川', '贵州',
  '云南', '西藏', '陕西', '甘肃', '青海', '宁夏', '新疆',
];

import { KOIL_SETTINGS_KEYS, DEFAULT_TANK_CAPACITY, MIN_TANK_CAPACITY, MAX_TANK_CAPACITY } from '@client/src/utils/koil-settings';
import { getKoilItem, setKoilItem } from '@client/src/utils/koil-storage';

function readNumber(key: string, fallback: number): number {
  try {
    const raw = getKoilItem(key);
    if (raw == null) return fallback;
    const n = Number(raw);
    if (Number.isFinite(n)) return n;
  } catch {
    // ignore
  }
  return fallback;
}

function readString(key: string, fallback: string): string {
  try {
    return getKoilItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

function writeString(key: string, value: string): void {
  try {
    setKoilItem(key, value);
  } catch {
    // ignore
  }
}

const STORAGE_KEYS = KOIL_SETTINGS_KEYS;

export function SettingsPanel({ onDataChanged, activeTab }: SettingsPanelProps) {
  const { theme, setTheme } = useTheme();
  const { t, mode, setMode } = useTranslation();

  const [defaultOilGrade, setDefaultOilGrade] = useState<string>(() =>
    readString(STORAGE_KEYS.defaultOilGrade, '92'),
  );
  const [defaultProvince, setDefaultProvince] = useState<string>(() =>
    readString(STORAGE_KEYS.defaultProvince, ''),
  );
  const hasConfiguredProvince = defaultProvince !== '';
  const { state: locationState } = useLocationPermission();
  const [remindThreshold, setRemindThreshold] = useState<number>(() =>
    readNumber(STORAGE_KEYS.remindThreshold, 100),
  );
  const [abnormalThreshold, setAbnormalThreshold] = useState<number>(() =>
    readNumber(STORAGE_KEYS.abnormalThreshold, 30),
  );
  const { enabled: priceAlertEnabled, toggle: togglePriceAlert } = usePriceAlert();
  const [followLastGrade, setFollowLastGrade] = useState<boolean>(() =>
    readString(STORAGE_KEYS.followLastGrade, 'on') === 'on',
  );
  const [tankCapacity, setTankCapacity] = useState<number>(() => {
    const raw = readNumber(STORAGE_KEYS.tankCapacity, DEFAULT_TANK_CAPACITY);
    return Math.max(MIN_TANK_CAPACITY, Math.min(MAX_TANK_CAPACITY, raw));
  });

  const [importOpen, setImportOpen] = useState(false);

  const [langPopoverOpen, setLangPopoverOpen] = useState(false);
  const [themePopoverOpen, setThemePopoverOpen] = useState(false);
  const [provincePopoverOpen, setProvincePopoverOpen] = useState(false);
  const [searchParams] = useSearchParams();
  const currentTab = searchParams.get('tab') || 'add';

  useEffect(() => {
    setLangPopoverOpen(false);
    setThemePopoverOpen(false);
  }, [currentTab]);

  const PRICE_DB_KEY = 'price-db-auto-update';
  const [priceDbStatus, setPriceDbStatus] = useState<{
    initialized: boolean;
    latestDate: string | null;
    lastPriceChangeDate: string | null;
    lastWindowChecked: string | null;
    versionDate: string | null;
    provinceCount: number;
    gradeCount: number;
    adjustmentCount: number;
    totalRecords: number;
    lastCheckedAt: string | null;
    lastUpdatedAt: string | null;
    initializedAt: string | null;
    autoUpdateEnabled: boolean;
    checkIntervalHours: number;
    lastUpdateMessage: string | null;
    isStranded: boolean;
  } | null>(null);
  const [priceDbLoading, setPriceDbLoading] = useState(true);
  const [priceDbUpdating, setPriceDbUpdating] = useState(false);
  const [autoUpdating, setAutoUpdating] = useState(false);
  const [deviationThreshold, setDeviationThreshold] = useState<number>(3);
  const [deviationThresholdLoading, setDeviationThresholdLoading] = useState(false);
  const [deviationThresholdSaving, setDeviationThresholdSaving] = useState(false);

  const [pendingItems, setPendingItems] = useState<KoilPricePendingItem[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [pendingActionId, setPendingActionId] = useState<string | null>(null);

  const loadPriceDbStatus = useCallback(async () => {
    try {
      const status = await getOilPriceDbStatus();
      setPriceDbStatus(status);
    } catch (error) {
      logger.error('加载油价数据库状态失败', error);
    } finally {
      setPriceDbLoading(false);
    }
  }, []);

  const loadPendingList = useCallback(async () => {
    try {
      setPendingLoading(true);
      const result = await getOilPricePending('pending');
      setPendingItems(result.items);
    } catch (error) {
      logger.error('加载待确认更新列表失败', error);
    } finally {
      setPendingLoading(false);
    }
  }, []);

  const handleAcceptPending = async (id: string, useSource: 'primary' | 'secondary') => {
    setPendingActionId(id);
    try {
      const result = await acceptOilPricePending(id, useSource);
      if (result.success) {
        toast.success('已采纳');
        await Promise.all([loadPendingList(), loadPriceDbStatus()]);
      } else {
        toast.error('采纳失败');
      }
    } catch (error) {
      logger.error('采纳待确认更新失败', error);
      toast.error('操作失败，请稍后重试');
    } finally {
      setPendingActionId(null);
    }
  };

  const handleIgnorePending = async (id: string) => {
    setPendingActionId(id);
    try {
      const result = await ignoreOilPricePending(id);
      if (result.success) {
        toast.success('已忽略');
        await Promise.all([loadPendingList(), loadPriceDbStatus()]);
      } else {
        toast.error('操作失败');
      }
    } catch (error) {
      logger.error('忽略待确认更新失败', error);
      toast.error('操作失败，请稍后重试');
    } finally {
      setPendingActionId(null);
    }
  };

  const handleUpdateNow = async () => {
    if (priceDbUpdating) return;
    setPriceDbUpdating(true);
    try {
      const result = await updateOilPriceDb();
      if (result.success) {
        const datePart = result.latestDate ? `（至 ${result.latestDate}）` : '';
        toast.success(`${result.message || '更新完成'}${datePart}`);
      } else {
        toast.error(result.message || '更新失败');
      }
      await Promise.all([loadPriceDbStatus(), loadPendingList()]);
    } catch (error) {
      logger.error('立即更新失败', error);
      toast.error('更新失败，请稍后重试');
    } finally {
      setPriceDbUpdating(false);
    }
  };

  const toggleAutoUpdate = async () => {
    if (autoUpdating || !priceDbStatus) return;
    const next = !priceDbStatus.autoUpdateEnabled;
    setAutoUpdating(true);
    try {
      const result = await setOilPriceDbAutoUpdate(next);
      setPriceDbStatus((prev) => (prev ? { ...prev, autoUpdateEnabled: result.enabled } : prev));
      toast.success(next ? '已开启自动更新' : '已关闭自动更新');
    } catch (error) {
      logger.error('切换自动更新失败', error);
      toast.error('切换失败，请稍后重试');
    } finally {
      setAutoUpdating(false);
    }
  };

  const handleThemeChange = useCallback(
    (mode: ThemeMode) => {
      setTheme(mode);
    },
    [setTheme],
  );

  const handleOilGradeChange = (grade: string) => {
    setDefaultOilGrade(grade);
    writeString(STORAGE_KEYS.defaultOilGrade, grade);
    toast.success(t('settings.defaultGradeUpdated'));
    window.dispatchEvent(new Event('koil-preferences-changed'));
  };

  const handleProvinceChange = (province: string) => {
    setDefaultProvince(province);
    writeString(STORAGE_KEYS.defaultProvince, province);
    toast.success(t('settings.defaultProvinceUpdated'));
    window.dispatchEvent(new Event('koil-preferences-changed'));
  };

  const effectiveDefaultProvince = hasConfiguredProvince
    ? defaultProvince
    : locationState.province || '北京';

  const handleRemindChange = (val: string) => {
    const n = Math.max(0, Math.min(1000, Number(val) || 0));
    setRemindThreshold(n);
    writeString(STORAGE_KEYS.remindThreshold, String(n));
  };

  const handleAbnormalChange = (val: string) => {
    const n = Math.max(5, Math.min(100, Number(val) || 30));
    setAbnormalThreshold(n);
    writeString(STORAGE_KEYS.abnormalThreshold, String(n));
    toast.success(t('settings.abnormalThresholdUpdated'));
  };

  const handleTankCapacityChange = (val: number) => {
    const n = Math.max(MIN_TANK_CAPACITY, Math.min(MAX_TANK_CAPACITY, Math.round(val)));
    setTankCapacity(n);
    writeString(STORAGE_KEYS.tankCapacity, String(n));
    toast.success(t('settings.tankCapacityUpdated'));
    window.dispatchEvent(new Event('koil-settings-updated'));
  };

  const toggleFollowLastGrade = () => {
    const next = !followLastGrade;
    setFollowLastGrade(next);
    writeString(STORAGE_KEYS.followLastGrade, next ? 'on' : 'off');
    toast.success(t('settings.followLastGradeUpdated'));
    window.dispatchEvent(new Event('koil-preferences-changed'));
  };

  const handleExport = async () => {
    try {
      const count = await exportKoilRecordsCsv();
      if (count === 0) {
        toast.info('暂无数据可导出');
        return;
      }
      toast.success(`已导出 ${count} 条记录`);
    } catch (error) {
      logger.error('导出失败', error);
      toast.error(t('settings.exportFailed'));
    }
  };

  const handleExportOilPriceHistory = async () => {
    try {
      const count = await exportOilPriceHistoryCsv();
      if (count === 0) {
        toast.info('暂无油价历史数据可导出');
        return;
      }
      toast.success(`已导出 ${count} 条油价历史记录`);
    } catch (error) {
      logger.error('导出油价历史失败', error);
      toast.error('导出油价历史失败');
    }
  };

  const handleExportJson = async () => {
    try {
      const settings: Record<string, string | number | boolean> = {};
      const KOIL_PREFIX = 'koil-';
      for (let i = 0; i < window.localStorage.length; i++) {
        const key = window.localStorage.key(i);
        if (key && key.startsWith(KOIL_PREFIX)) {
          const raw = window.localStorage.getItem(key);
          if (raw != null) settings[key] = raw;
        }
      }
      const count = await exportBackupJson(settings);
      if (count === 0) {
        toast.info('暂无数据可导出');
        return;
      }
      toast.success(`已导出完整备份（${count} 条记录）`);
    } catch (error) {
      logger.error('导出备份失败', error);
      toast.error(t('settings.exportFailed'));
    }
  };

  useEffect(() => {
    const evt = new CustomEvent('koil-settings-updated');
    window.dispatchEvent(evt);
  }, [defaultOilGrade, defaultProvince, remindThreshold, abnormalThreshold, priceAlertEnabled, tankCapacity]);

  useEffect(() => {
    const onPrefsChanged = (): void => {
      const stored = readString(STORAGE_KEYS.defaultProvince, '');
      if (stored && stored !== defaultProvince) {
        setDefaultProvince(stored);
      }
    };
    window.addEventListener('koil-preferences-changed', onPrefsChanged);
    return () => window.removeEventListener('koil-preferences-changed', onPrefsChanged);
  }, [defaultProvince]);

  const autoCheckPriceDb = useCallback(async () => {
    try {
      const status = await getOilPriceDbStatus();
      if (!status.autoUpdateEnabled || !status.lastCheckedAt) return;
      const lastCheck = new Date(status.lastCheckedAt).getTime();
      const hoursSince = (Date.now() - lastCheck) / (60 * 60 * 1000);
      if (hoursSince < 7.5) return;
      const result = await updateOilPriceDb();
      if (result.success && result.newAdjustments > 0 && result.latestDate) {
        toast.success(`油价已自动更新至 ${result.latestDate}，新增 ${result.newRecords} 条记录`);
      }
      await loadPriceDbStatus();
      await loadPendingList();
    } catch (error) {
      logger.info('油价自动检查跳过或失败', error);
    }
  }, [loadPriceDbStatus, loadPendingList]);

  useEffect(() => {
    void loadPriceDbStatus();
    void loadPendingList();
    setDeviationThresholdLoading(true);
    getPriceDeviationThreshold()
      .then((res) => {
        setDeviationThreshold(res.thresholdPercent);
      })
      .catch((error) => {
        logger.error('加载偏差阈值失败', error);
      })
      .finally(() => {
        setDeviationThresholdLoading(false);
      });
    void autoCheckPriceDb();
  }, [loadPriceDbStatus, loadPendingList, autoCheckPriceDb]);

  const handleDeviationThresholdChange = (value: number): void => {
    if (Number.isNaN(value) || value <= 0 || value > 50) return;
    setDeviationThreshold(value);
    setDeviationThresholdSaving(true);
    setPriceDeviationThreshold(value)
      .then((res) => {
        setDeviationThreshold(res.thresholdPercent);
        toast.success(`偏差阈值已设为 ${res.thresholdPercent}%`);
      })
      .catch((error) => {
        logger.error('保存偏差阈值失败', error);
        const msg = (error as { response?: { data?: { error?: { message?: string } } } })
          ?.response?.data?.error?.message || '保存失败';
        toast.error(msg);
      })
      .finally(() => {
        setDeviationThresholdSaving(false);
      });
  };

  const themeOptions: { key: ThemeMode; label: string; icon: typeof Sun }[] = [
    { key: 'system', label: t('settings.themeFollowSystem'), icon: Monitor },
    { key: 'light', label: t('settings.themeLight'), icon: Sun },
    { key: 'dark', label: t('settings.themeDark'), icon: Moon },
  ];

  const languageOptions: { key: LanguageMode; label: string; icon: typeof Globe }[] = [
    { key: 'system', label: t('settings.languageFollowSystem'), icon: Monitor },
    { key: 'zh-CN', label: t('settings.langZhCN'), icon: Globe },
    { key: 'zh-TW', label: t('settings.langZhTW'), icon: Globe },
    { key: 'en', label: t('settings.langEn'), icon: Globe },
  ];

  const currentLanguageLabel =
    languageOptions.find((o) => o.key === mode)?.label ?? mode;
  const currentThemeLabel =
    themeOptions.find((o) => o.key === theme)?.label ?? theme;

  const oilGradeOptions = [
    { key: '92', label: '92号' },
    { key: '95', label: '95号' },
    { key: '98', label: '98号' },
    { key: '0', label: '0号' },
  ];
  const currentOilGradeLabel =
    oilGradeOptions.find((o) => o.key === defaultOilGrade)?.label ?? defaultOilGrade;

  const provinceWarn =
    !hasConfiguredProvince && locationState.provinceSource !== 'IP 归属地';

  return (
    <div className="space-y-6 pb-8 sm:space-y-8">
      {/* 通用设置 */}
      <section className="glass rounded-2xl p-4">
        <SectionHeader icon={<Settings className="size-4" />} title={t('settings.general')} />

        <div className="mt-4 space-y-3">
          <div className="border-b border-border/40 pb-3">
            <Select value={mode} onValueChange={(v) => setMode(v as LanguageMode)}>
              <SelectTrigger asChild className="!w-full !border-0 !bg-transparent !p-0 !gap-3 !h-auto !shadow-none !rounded-none data-[state=open]:[&_svg]:rotate-0">
                <SettingSelectRow
                  label={t('settings.language')}
                  value={currentLanguageLabel}
                />
              </SelectTrigger>
              <SelectContent align="end" className="max-h-72 min-w-[10rem] ![--radix-select-trigger-width:0]">
                {languageOptions.map((opt) => {
                  const Icon = opt.icon;
                  return (
                    <SelectItem key={opt.key} value={opt.key}>
                      <Icon className="size-4 shrink-0 opacity-70" />
                      <span className="flex-1 truncate">{opt.label}</span>
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </div>

          <div className="border-b border-border/40 pb-3">
            <Select value={theme} onValueChange={(v) => handleThemeChange(v as ThemeMode)}>
              <SelectTrigger asChild className="!w-full !border-0 !bg-transparent !p-0 !gap-3 !h-auto !shadow-none !rounded-none data-[state=open]:[&_svg]:rotate-0">
                <SettingSelectRow
                  label={t('settings.appearance')}
                  value={currentThemeLabel}
                />
              </SelectTrigger>
              <SelectContent align="end" className="max-h-72 min-w-[10rem] ![--radix-select-trigger-width:0]">
                {themeOptions.map((opt) => {
                  const Icon = opt.icon;
                  return (
                    <SelectItem key={opt.key} value={opt.key}>
                      <Icon className="size-4 shrink-0 opacity-70" />
                      <span className="flex-1 truncate">{opt.label}</span>
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-1.5 border-b border-border/40 pb-3">
            <Select value={effectiveDefaultProvince} onValueChange={handleProvinceChange}>
              <SelectTrigger asChild className="!w-full !border-0 !bg-transparent !p-0 !gap-3 !h-auto !shadow-none !rounded-none data-[state=open]:[&_svg]:rotate-0">
                <SettingSelectRow
                  label={t('settings.defaultProvince')}
                  value={effectiveDefaultProvince}
                  destructive={provinceWarn}
                />
              </SelectTrigger>
              <SelectContent align="end" className="max-h-72 min-w-[10rem] ![--radix-select-trigger-width:0]">
                {PROVINCES.map((p: string) => (
                  <SelectItem key={p} value={p}>
                    {p}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {provinceWarn ? (
              <div className="flex items-center gap-1.5 text-[11px] font-medium text-destructive">
                <AlertTriangle className="size-3.5 shrink-0" />
                <span>定位失败，请选择你的默认省份</span>
              </div>
            ) : locationState.provinceSource === 'IP 归属地' && !hasConfiguredProvince ? (
              <p className="text-[11px] text-primary/80">当前位置自动匹配</p>
            ) : (
              <p className="text-[11px] text-muted-foreground">{t('settings.defaultProvinceDesc')}</p>
            )}
          </div>
          <div className="border-b border-border/40 pb-3">
            <SettingRow
              label={
                <span>
                  {t('settings.tankCapacity')}
                  <span className="ml-1 text-xs font-normal text-muted-foreground tabular-nums">
                    ({tankCapacity}L)
                  </span>
                </span>
              }
              desc={t('settings.tankCapacityDesc')}
            >
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => handleTankCapacityChange(tankCapacity - 1)}
                  className="flex size-7 items-center justify-center rounded-full bg-primary-soft text-primary transition-all hover:bg-primary-soft/80 active:scale-95"
                  aria-label="减少油箱容量"
                >
                  <MinusIcon className="size-3.5" />
                </button>
                <span className="w-10 text-center text-sm font-semibold tabular-nums text-foreground">
                  {tankCapacity}
                </span>
                <button
                  type="button"
                  onClick={() => handleTankCapacityChange(tankCapacity + 1)}
                  className="flex size-7 items-center justify-center rounded-full bg-primary-soft text-primary transition-all hover:bg-primary-soft/80 active:scale-95"
                  aria-label="增加油箱容量"
                >
                  <PlusIcon className="size-3.5" />
                </button>
              </div>
            </SettingRow>
          </div>

          <div className="pt-3">
            <SettingRow label={t('settings.followLastGrade')} desc={followLastGrade ? t('settings.defaultGradeDesc') : t('settings.followLastGradeDesc')}>
              <button
                type="button"
                onClick={toggleFollowLastGrade}
                role="switch"
                aria-checked={followLastGrade}
                className={`relative h-6 w-11 shrink-0 rounded-full transition-all duration-300 ${
                  followLastGrade ? 'bg-primary' : 'bg-muted/40'
                }`}
              >
                <span
                  className={`absolute top-0.5 size-5 rounded-full bg-white shadow-sm transition-all duration-300 ${
                    followLastGrade ? 'left-[22px]' : 'left-0.5'
                  }`}
                />
              </button>
            </SettingRow>

            <div
              className={`overflow-hidden transition-all duration-300 ease-out ${
                followLastGrade ? 'max-h-0 opacity-0' : 'max-h-[72px] opacity-100'
              }`}
            >
              <div className="pt-3 mt-3 border-t border-border/40">
                <div className="flex w-full gap-2">
                  {oilGradeOptions.map((opt) => {
                    const active = defaultOilGrade === opt.key;
                    return (
                      <button
                        key={opt.key}
                        type="button"
                        onClick={() => handleOilGradeChange(opt.key)}
                        className={`flex-1 rounded-full py-2 text-xs font-medium transition-all ${
                          active
                            ? 'gradient-primary text-white shadow-sm shadow-black/25'
                            : 'glass-soft text-foreground-secondary'
                        }`}
                      >
                        {opt.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

        </div>
      </section>

      {/* 统计与提醒 */}
      <section className="glass rounded-2xl p-4">
        <SectionHeader icon={<AlertTriangle className="size-4" />} title={t('settings.statsAndReminders')} />

        <div className="mt-4 space-y-3">
          <div className="border-b border-border/40 pb-3">
            <SettingRow label={t('settings.oilReminderThreshold')} desc={t('settings.oilReminderDesc', { value: remindThreshold })}>
              <div className="flex items-center gap-2">
                <input
                  type="range"
                  min={0}
                  max={500}
                  step={10}
                  value={remindThreshold}
                  onChange={(e) => handleRemindChange(e.target.value)}
                  className="w-32 accent-primary"
                />
                <span className="w-12 text-right text-xs tabular-nums text-foreground-secondary">
                  {remindThreshold} km
                </span>
              </div>
            </SettingRow>
          </div>

          <div className="pt-3">
            <SettingRow label={t('settings.abnormalThreshold')} desc={t('settings.abnormalThresholdDesc', { value: abnormalThreshold })}>
              <div className="flex items-center gap-2">
                <input
                  type="range"
                  min={5}
                  max={100}
                  step={5}
                  value={abnormalThreshold}
                  onChange={(e) => handleAbnormalChange(e.target.value)}
                  className="w-32 accent-primary"
                />
                <span className="w-10 text-right text-xs tabular-nums text-foreground-secondary">
                  {abnormalThreshold}%
                </span>
              </div>
            </SettingRow>
          </div>

          <div className="border-t border-border/40 pt-3">
            <SettingRow label={t('settings.oilPriceAlert')} desc={t('settings.oilPriceAlertDesc')}>
              <button
                type="button"
                onClick={togglePriceAlert}
                role="switch"
                aria-checked={priceAlertEnabled}
                className={`relative h-6 w-11 shrink-0 rounded-full transition-all duration-300 ${
                  priceAlertEnabled ? 'bg-primary' : 'bg-muted/40'
                }`}
              >
                <span
                  className={`absolute top-0.5 size-5 rounded-full bg-white shadow-sm transition-all duration-300 ${
                    priceAlertEnabled ? 'left-[22px]' : 'left-0.5'
                  }`}
                />
              </button>
            </SettingRow>
          </div>
        </div>
      </section>

      {/* 历史油价数据库 */}
      <section className="glass rounded-2xl p-4">
        <SectionHeader icon={<Database className="size-4" />} title="历史油价数据库" />

        <div className="mt-4 space-y-4">
          {priceDbLoading ? (
            <div className="py-4 text-center text-xs text-muted-foreground">
              <RefreshCw className="mx-auto mb-2 size-4 animate-spin text-primary" />
              加载中...
            </div>
          ) : (
            <>
              <div className="rounded-2xl bg-surface-1/40 p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs text-muted-foreground">数据截至</p>
                    <p className="mt-0.5 text-lg font-semibold tabular-nums text-foreground">
                      {priceDbStatus?.initialized && priceDbStatus?.versionDate
                        ? priceDbStatus.versionDate
                        : '未初始化'}
                    </p>
                    {priceDbStatus?.initialized && priceDbStatus.lastPriceChangeDate && (
                      <p className="mt-0.5 text-[11px] text-muted-foreground">
                        最近变价：{priceDbStatus.lastPriceChangeDate}
                        {priceDbStatus.isStranded && priceDbStatus.versionDate && priceDbStatus.versionDate > priceDbStatus.lastPriceChangeDate && (
                          <span className="text-warn"> · 搁浅，价格维持 {priceDbStatus.lastPriceChangeDate.slice(5).replace('-', '月')}日</span>
                        )}
                      </p>
                    )}
                  </div>
                  <div className="flex size-10 items-center justify-center rounded-2xl bg-primary/12 text-primary">
                    {priceDbStatus?.initialized ? (
                      <Check className="size-5" />
                    ) : (
                      <AlertTriangle className="size-5" />
                    )}
                  </div>
                </div>
                <p className="mt-2 text-[11px] text-muted-foreground">
                  {priceDbStatus?.initialized
                    ? '按国内成品油调价窗口自动跟进，含搁浅窗口'
                    : '油价数据待初始化，首次查价时自动灌入'}
                </p>
              </div>

              <div className="grid grid-cols-4 gap-2">
                <div className="rounded-xl bg-surface-1/30 px-2.5 py-3 text-center">
                  <MapPin className="mx-auto mb-1 size-3.5 text-primary/70" />
                  <p className="text-sm font-semibold tabular-nums text-foreground">
                    {priceDbStatus?.provinceCount ?? 0}
                  </p>
                  <p className="text-[10px] text-muted-foreground">省份</p>
                </div>
                <div className="rounded-xl bg-surface-1/30 px-2.5 py-3 text-center">
                  <Gauge className="mx-auto mb-1 size-3.5 text-primary/70" />
                  <p className="text-sm font-semibold tabular-nums text-foreground">
                    {priceDbStatus?.gradeCount ?? 0}
                  </p>
                  <p className="text-[10px] text-muted-foreground">油号</p>
                </div>
                <div className="rounded-xl bg-surface-1/30 px-2.5 py-3 text-center">
                  <Calendar className="mx-auto mb-1 size-3.5 text-primary/70" />
                  <p className="text-sm font-semibold tabular-nums text-foreground">
                    {priceDbStatus?.adjustmentCount ?? 0}
                  </p>
                  <p className="text-[10px] text-muted-foreground">调价</p>
                </div>
                <div className="rounded-xl bg-surface-1/30 px-2.5 py-3 text-center">
                  <Database className="mx-auto mb-1 size-3.5 text-primary/70" />
                  <p className="text-sm font-semibold tabular-nums text-foreground">
                    {priceDbStatus?.totalRecords ?? 0}
                  </p>
                  <p className="text-[10px] text-muted-foreground">记录</p>
                </div>
              </div>

              {priceDbStatus?.lastUpdatedAt && (
                <p className="text-[11px] text-muted-foreground/80">
                  最近更新：{new Date(priceDbStatus.lastUpdatedAt).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}
                </p>
              )}

              {priceDbStatus?.lastUpdateMessage && (
                <p className="text-[11px] text-muted-foreground/60">
                  {priceDbStatus.lastUpdateMessage}
                </p>
              )}

              {/* 待确认更新 */}
              <div className="border-t border-border/40 pt-4">
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-xs font-medium text-foreground">待确认更新</p>
                  {pendingItems.length > 0 && (
                    <span className="rounded-full bg-warn/15 px-2 py-0.5 text-[10px] font-medium text-warn">
                      {pendingItems.length} 条
                    </span>
                  )}
                </div>
                {pendingLoading ? (
                  <div className="py-4 text-center text-xs text-muted-foreground">
                    <RefreshCw className="mx-auto mb-2 size-4 animate-spin text-primary" />
                    加载中...
                  </div>
                ) : pendingItems.length === 0 ? (
                  <div className="rounded-2xl bg-surface-1/30 py-5 text-center">
                    <Check className="mx-auto mb-2 size-5 text-success/70" />
                    <p className="text-xs text-muted-foreground">暂无待确认更新</p>
                  </div>
                ) : (
                  <div className="space-y-2.5">
                    {pendingItems.map((item) => {
                      const hasDiff = item.primaryPrice !== null && item.secondaryPrice !== null && item.primaryPrice !== item.secondaryPrice;
                      return (
                        <div
                          key={item.id}
                          className="rounded-2xl bg-surface-1/40 p-3.5 transition-all"
                        >
                          <div className="flex items-center justify-between">
                            <div>
                              <p className="text-sm font-medium text-foreground">
                                {item.province} · {item.grade}
                              </p>
                              <p className="mt-0.5 text-[11px] text-muted-foreground">
                                生效日期：{item.effectiveDate}
                              </p>
                            </div>
                          </div>
                          <div className="mt-2.5 flex items-center gap-2">
                            <div className="flex-1 rounded-xl bg-surface-1/50 px-3 py-2">
                              <p className="text-[10px] text-muted-foreground">{item.primarySource || '主源'}</p>
                              <p className={`text-sm font-semibold tabular-nums ${hasDiff ? 'text-warn' : 'text-foreground'}`}>
                                {item.primaryPrice !== null ? `¥${item.primaryPrice.toFixed(2)}` : '--'}
                              </p>
                            </div>
                            <div className="text-[10px] text-muted-foreground">vs</div>
                            <div className="flex-1 rounded-xl bg-surface-1/50 px-3 py-2">
                              <p className="text-[10px] text-muted-foreground">{item.secondarySource || '备源'}</p>
                              <p className={`text-sm font-semibold tabular-nums ${hasDiff ? 'text-warn' : 'text-foreground'}`}>
                                {item.secondaryPrice !== null ? `¥${item.secondaryPrice.toFixed(2)}` : '--'}
                              </p>
                            </div>
                          </div>
                          <div className="mt-3 flex items-center gap-2">
                            <Button
                              variant="default"
                              size="sm"
                              className="flex-1"
                              onClick={() => void handleAcceptPending(item.id, 'primary')}
                              disabled={pendingActionId === item.id || item.primaryPrice === null}
                            >
                              采纳主源
                            </Button>
                            <Button
                              variant="default"
                              size="sm"
                              className="flex-1"
                              onClick={() => void handleAcceptPending(item.id, 'secondary')}
                              disabled={pendingActionId === item.id || item.secondaryPrice === null}
                            >
                              采纳备源
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => void handleIgnorePending(item.id)}
                              disabled={pendingActionId === item.id}
                              className="text-muted-foreground"
                            >
                              忽略
                            </Button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="flex items-center gap-2">
                <Button
                  variant="default"
                  size="sm"
                  className="flex-1"
                  onClick={handleUpdateNow}
                  disabled={priceDbUpdating}
                >
                  <RefreshCw className={`size-3.5 ${priceDbUpdating ? 'animate-spin' : ''}`} />
                  {priceDbUpdating ? '更新中...' : '立即更新'}
                </Button>
              </div>

              <div className="border-t border-border/40 pt-3">
                <SettingRow
                  label="自动更新"
                  desc={`每 ${priceDbStatus?.checkIntervalHours ?? 8} 小时检测一次调价，发布后生效`}
                >
                  <button
                    type="button"
                    onClick={toggleAutoUpdate}
                    role="switch"
                    aria-checked={priceDbStatus?.autoUpdateEnabled ?? true}
                    disabled={autoUpdating}
                    className={`relative h-6 w-11 shrink-0 rounded-full transition-all duration-300 ${
                      priceDbStatus?.autoUpdateEnabled ? 'bg-primary' : 'bg-muted/40'
                    } ${autoUpdating ? 'opacity-50' : ''}`}
                  >
                    <span
                      className={`absolute top-0.5 size-5 rounded-full bg-white shadow-sm transition-all duration-300 ${
                        priceDbStatus?.autoUpdateEnabled ? 'left-[22px]' : 'left-0.5'
                      }`}
                    />
                  </button>
                </SettingRow>
               </div>

              {/* 单价偏差阈值 */}
               <div className="border-t border-border/40 pt-3">
                 <div className="flex items-center justify-between">
                   <div>
                     <p className="text-xs font-medium text-foreground">单价偏差阈值</p>
                     <p className="mt-0.5 text-[11px] text-muted-foreground">
                       实际单价与库内确认价偏差超过此比例时提示
                     </p>
                   </div>
                   <div className="flex items-center gap-2">
                     <button
                       type="button"
                       onClick={() => handleDeviationThresholdChange(Math.max(1, deviationThreshold - 0.5))}
                        className="flex size-7 items-center justify-center rounded-full bg-surface-1/60 text-foreground-secondary transition-all"
                       aria-label="减小阈值"
                     >
                       <MinusIcon className="size-3.5" />
                     </button>
                     <span className="w-14 text-center text-sm font-semibold tabular-nums text-foreground">
                       {deviationThreshold.toFixed(1)}%
                     </span>
                     <button
                       type="button"
                       onClick={() => handleDeviationThresholdChange(Math.min(50, deviationThreshold + 0.5))}
                        className="flex size-7 items-center justify-center rounded-full bg-surface-1/60 text-foreground-secondary transition-all"
                       aria-label="增大阈值"
                     >
                       <PlusIcon className="size-3.5" />
                     </button>
                   </div>
                 </div>
                 {deviationThresholdSaving && (
                   <p className="mt-1 text-[10px] text-muted-foreground/60">
                     保存中...
                   </p>
                 )}
               </div>
            </>
          )}
        </div>
      </section>

       {/* 数据管理 */}
      <section className="glass rounded-2xl p-4">
        <SectionHeader icon={<Fuel className="size-4" />} title={t('settings.dataManagement')} />

        <div className="mt-3 space-y-1">
          <DataActionRow
            icon={<Download className="size-4" />}
            title={t('settings.exportCsv')}
            desc={t('settings.exportCsvDesc')}
            onClick={handleExport}
          />
          <DataActionRow
            icon={<TrendingUp className="size-4" />}
            title="导出油价历史记录"
            desc="全国31省份×4油号历史调价记录 CSV"
            onClick={handleExportOilPriceHistory}
          />
          <DataActionRow
            icon={<FileJson className="size-4" />}
            title={t('settings.exportJson')}
            desc={t('settings.exportJsonDesc')}
            onClick={handleExportJson}
          />
          <DataActionRow
            icon={<Upload className="size-4" />}
            title={t('settings.importData')}
            desc={t('settings.importDataDesc')}
            onClick={() => setImportOpen(true)}
          />
          <div className="flex items-center justify-between border-t border-border/40 px-1 py-3">
            <div className="flex items-center gap-3">
              <div className="flex size-9 items-center justify-center rounded-2xl bg-success/15 text-success">
                <Cloud className="size-4" />
              </div>
              <div>
                <p className="text-sm font-medium text-foreground">{t('settings.cloudSync')}</p>
                <p className="text-xs text-muted-foreground">
                  {t('settings.cloudSynced')} · {t('settings.cloudSyncLastSync', { time: t('settings.cloudSyncJustNow') })}
                </p>
              </div>
            </div>
            <span className="flex items-center gap-1 text-xs text-success">
              <Check className="size-3" />
              {t('settings.cloudSyncOn')}
            </span>
          </div>
        </div>
      </section>

      {/* 关于 */}
      <section className="glass rounded-2xl p-4">
        <SectionHeader icon={<Info className="size-4" />} title={t('settings.about')} />

        <div className="mt-4 space-y-4 text-sm">
          <div>
            <p className="font-medium text-foreground">{t('settings.calcMethod')}</p>
            <ul className="mt-2 space-y-1.5 text-xs text-muted-foreground">
              <li>· {t('settings.calcDistance')}</li>
              <li>· {t('settings.calcAvgOil')}</li>
              <li>· {t('settings.calcCostPerKm')}</li>
              <li>· {t('settings.calcFirstRecord')}</li>
              <li>· {t('settings.calcRangeAvg')}</li>
            </ul>
          </div>

          <div className="border-t border-border/40 pt-4">
            <p className="font-medium text-foreground">{t('settings.oilPriceSource')}</p>
            <p className="mt-1.5 text-xs text-muted-foreground">
              {t('settings.oilPriceSourceDesc')}
            </p>
          </div>

          <div className="border-t border-border/40 pt-4">
            <p className="font-medium text-foreground">{t('settings.privacy')}</p>
            <p className="mt-1.5 text-xs text-muted-foreground">
              {t('settings.privacyDesc')}
            </p>
          </div>

          <div className="border-t border-border/40 pt-4 text-center">
            <p className="text-sm font-semibold text-foreground">KOiL</p>
            <p className="mt-0.5 text-[11px] text-muted-foreground sm:text-xs">{t('app.tagline')}</p>
            <p className="mt-3 text-xs text-muted-foreground/60">{t('settings.version', { version: '1.2.0' })}</p>
          </div>
        </div>
      </section>

      {/* 导入对话框 */}
      <ImportDialog open={importOpen} onOpenChange={setImportOpen} onImported={() => {
        setImportOpen(false);
        onDataChanged?.();
      }} />

    </div>
  );
}

interface SectionHeaderProps {
  icon: React.ReactNode;
  title: string;
}

function SectionHeader({ icon, title }: SectionHeaderProps) {
  return (
    <div className="flex items-center gap-2">
      <div className="flex size-8 items-center justify-center rounded-2xl bg-primary/12 text-primary">
        {icon}
      </div>
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
    </div>
  );
}

interface SettingSelectRowProps {
  label: string;
  value: string;
  destructive?: boolean;
}

const SettingSelectRow = React.forwardRef<HTMLButtonElement, SettingSelectRowProps>(
  ({ label, value, destructive, ...props }, ref) => {
    return (
      <button
        ref={ref}
        type="button"
         className="flex w-full items-center justify-between gap-3 text-left transition-all"
        {...props}
      >
        <span className="text-sm font-medium text-foreground">{label}</span>
        <span className="flex items-center gap-1.5 shrink-0 min-w-0">
          <span
            className={`truncate text-sm ${
              destructive ? 'text-destructive font-medium' : 'text-primary'
            }`}
          >
            {value}
          </span>
          <ChevronsUpDown
            className={`size-4 shrink-0 ${
              destructive ? 'text-destructive/70' : 'text-muted-foreground/60'
            }`}
          />
        </span>
      </button>
    );
  },
);
SettingSelectRow.displayName = 'SettingSelectRow';

interface SelectMenuProps {
  children: React.ReactNode;
  scrollable?: boolean;
  'data-koil-settings-popover'?: string;
}

function SelectMenu({ children, scrollable, 'data-koil-settings-popover': dataAttr }: SelectMenuProps) {
  const scrollRef = React.useRef<HTMLDivElement>(null);
  const scrollTimerRef = React.useRef<number | null>(null);

  const startScroll = (direction: 'up' | 'down') => {
    const el = scrollRef.current;
    if (!el) return;
    if (scrollTimerRef.current) return;
    const step = direction === 'down' ? 40 : -40;
    el.scrollBy({ top: step });
    scrollTimerRef.current = window.setInterval(() => {
      const node = scrollRef.current;
      if (node) node.scrollBy({ top: step });
    }, 80);
  };

  const stopScroll = () => {
    if (scrollTimerRef.current) {
      clearInterval(scrollTimerRef.current);
      scrollTimerRef.current = null;
    }
  };

  const handleScrollBtnPointerDown = (
    e: React.PointerEvent<HTMLButtonElement>,
    direction: 'up' | 'down',
  ) => {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    if (e.pointerType !== 'mouse') {
      startScroll(direction);
    }
  };

  const handleScrollBtnPointerUp = (e: React.PointerEvent<HTMLButtonElement>) => {
    e.preventDefault();
    stopScroll();
  };

  React.useEffect(() => {
    return () => stopScroll();
  }, []);

  return (
    <PopoverContent
      align="end"
      sideOffset={8}
      data-koil-settings-popover={dataAttr}
      className={`glass-strong min-w-[8rem] w-full rounded-2xl border border-white/20 p-2 shadow-xl shadow-black/15 ${
        scrollable ? 'max-h-80 flex flex-col' : ''
      }`}
    >
      {scrollable && (
        <button
          type="button"
          aria-label="向上滚动"
          className="flex h-11 shrink-0 items-center justify-center text-muted-foreground/60 hover:text-foreground transition-colors touch-none select-none"
          onMouseEnter={() => startScroll('up')}
          onMouseLeave={stopScroll}
          onTouchStart={(e) => { e.preventDefault(); startScroll('up'); }}
          onTouchEnd={(e) => { e.preventDefault(); stopScroll(); }}
          onPointerDown={(e) => handleScrollBtnPointerDown(e, 'up')}
          onPointerUp={handleScrollBtnPointerUp}
          onPointerCancel={handleScrollBtnPointerUp}
        >
          <ChevronUp className="size-4" />
        </button>
      )}
      <div ref={scrollRef} className={`space-y-0.5 scrollbar-none ${scrollable ? 'overflow-y-auto flex-1' : ''}`}>
        {children}
      </div>
      {scrollable && (
        <button
          type="button"
          aria-label="向下滚动"
          className="flex h-11 shrink-0 items-center justify-center text-muted-foreground/60 hover:text-foreground transition-colors touch-none select-none"
          onMouseEnter={() => startScroll('down')}
          onMouseLeave={stopScroll}
          onTouchStart={(e) => { e.preventDefault(); startScroll('down'); }}
          onTouchEnd={(e) => { e.preventDefault(); stopScroll(); }}
          onPointerDown={(e) => handleScrollBtnPointerDown(e, 'down')}
          onPointerUp={handleScrollBtnPointerUp}
          onPointerCancel={handleScrollBtnPointerUp}
        >
          <ChevronDown className="size-4" />
        </button>
      )}
    </PopoverContent>
  );
}

interface SettingRowProps {
  label: React.ReactNode;
  desc: string;
  children: React.ReactNode;
}

function SettingRow({ label, desc, children }: SettingRowProps) {
  return (
      <div className="flex items-center justify-between gap-4 pb-3 last:pb-0">
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-foreground">{label}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{desc}</p>
      </div>
      <div className="shrink-0">{children}</div>
    </div>
  );
}

interface DataActionRowProps {
  icon: React.ReactNode;
  title: string;
  desc: string;
  onClick: () => void;
}

function DataActionRow({ icon, title, desc, onClick }: DataActionRowProps) {
  return (
    <button
      type="button"
      onClick={onClick}
       className="flex w-full items-center gap-3 rounded-2xl px-1 py-2.5 text-left transition-all duration-200"
    >
      <div className="flex size-9 items-center justify-center rounded-2xl bg-primary/12 text-primary">
        {icon}
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-foreground">{title}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{desc}</p>
      </div>
      <ChevronRight className="size-4 shrink-0 text-muted-foreground/60" />
    </button>
  );
}
