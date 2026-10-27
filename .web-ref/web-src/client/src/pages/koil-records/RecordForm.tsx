import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { useForm } from 'react-hook-form';

import { zodResolver } from '@hookform/resolvers/zod';
import { format } from 'date-fns';
import { z } from 'zod';
import {
  PlusIcon,
  CalendarIcon,
  Fuel as FuelIcon,
  HistoryIcon,
  CheckIcon,
  CheckCircle2Icon,
  Loader2Icon,
  MapPinIcon,
  DropletsIcon,
  AlertCircleIcon,
  RefreshCwIcon,
  GaugeIcon,
  WalletIcon,
  AlertTriangleIcon,
  ChevronDownIcon,
  RouteIcon,
  XIcon,
  DollarSignIcon,
} from 'lucide-react';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { useLocationPermission } from '@client/src/hooks/use-location-permission';
import { LocationPermissionDialog } from '@client/src/components/location-permission-dialog';
import { OilPriceCard } from '@client/src/components/oil-price-card/OilPriceCard';
import { useTranslation } from '@client/src/hooks/useTranslation';

import { Button } from '@client/src/components/ui/button';
import { Input } from '@client/src/components/ui/input';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@client/src/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@client/src/components/ui/select';

import {
  createKoilRecord,
  getLatestKoilRecord,
  getTodayOilPriceFromDb,
  getProvinces,
  precheckKoilRecord,
  checkPriceDeviation,
} from '@client/src/api/koil-records';
import { PaymentSummaryCard } from '@client/src/components/koil/PaymentSummaryCard';
import type { DuplicateCheckResponse, OilPriceResponse } from '@shared/api.interface';
import type {
  CreateKoilRecordRequest,
  LastKoilRecord,
  OilGrade,
  OilPriceInfo,
  ProvinceInfo,
} from '@shared/api.interface';
import { showConfirm } from '@lark-apaas/client-toolkit';

type RecordFormData = {
  reoilDate: Date;
  mileage: string;
  oilAmount: string;
  payableAmount?: string;
  actualAmount: string;
  oilGrade: string;
  oilPrice?: string;
};

interface RecordFormProps {
  onSuccess: () => void;
  onViewRecord?: () => void;
}

import { getTankCapacity } from '@client/src/utils/koil-settings';
import { getKoilItem, setKoilItem } from '@client/src/utils/koil-storage';

function getDefaultOilGrade(): string {
  if (typeof window === 'undefined') return '92';
  try {
    const saved = getKoilItem('default-grade');
    if (saved === '92' || saved === '95' || saved === '98' || saved === '0') return saved;
  } catch {
    // ignore
  }
  return '92';
}

function getFollowLastGrade(): boolean {
  if (typeof window === 'undefined') return true;
  try {
    return getKoilItem('follow-last-grade') !== 'off';
  } catch {
    return true;
  }
}

const OIL_PRICE_CACHE_KEY = 'oil-price-cache';
const OIL_PRICE_CACHE_TTL_MS = 30 * 60 * 1000;

function loadOilPriceCacheFromStorage(): Map<string, OilPriceResponse> {
  if (typeof window === 'undefined') return new Map();
  try {
    const raw = getKoilItem(OIL_PRICE_CACHE_KEY);
    if (!raw) return new Map();
    const parsed = JSON.parse(raw) as Record<string, { data: OilPriceResponse; ts: number }>;
    const map = new Map<string, OilPriceResponse>();
    const now = Date.now();
    for (const [province, entry] of Object.entries(parsed)) {
      if (now - entry.ts < OIL_PRICE_CACHE_TTL_MS) {
        map.set(province, entry.data);
      }
    }
    return map;
  } catch {
    return new Map();
  }
}

function persistOilPriceCacheToStorage(cache: Map<string, OilPriceResponse>): void {
  if (typeof window === 'undefined') return;
  try {
    const obj: Record<string, { data: OilPriceResponse; ts: number }> = {};
    const now = Date.now();
    for (const [province, data] of cache.entries()) {
      obj[province] = { data, ts: now };
    }
    setKoilItem(OIL_PRICE_CACHE_KEY, JSON.stringify(obj));
  } catch {
    // 存储失败不影响主流程
  }
}

export function RecordForm({ onSuccess, onViewRecord }: RecordFormProps) {
  const { t } = useTranslation();

  const gradeOptions = useMemo<{ value: OilGrade; label: string }[]>(() => [
    { value: '92', label: t('grade.92') },
    { value: '95', label: t('grade.95') },
    { value: '98', label: t('grade.98') },
    { value: '0', label: t('grade.diesel') },
  ], [t]);

  const koilRecordSchema = useMemo(() => {
    const maxTankCapacity = getTankCapacity();
    return z.object({
      reoilDate: z.date({ message: t('val.dateRequired') }),
      mileage: z.string().min(1, t('val.mileageRequired')).refine(
        (v: string) => !Number.isNaN(Number(v)) && Number(v) > 0,
        { message: t('val.mileageMustPositive') },
      ),
      oilAmount: z.string().min(1, t('val.oilAmountRequired')).refine(
        (v: string) => !Number.isNaN(Number(v)) && Number(v) > 0,
        { message: t('val.oilAmountMustPositive') },
      ).refine(
        (v: string) => Number.isNaN(Number(v)) || Number(v) >= 1,
        { message: t('val.oilAmountTooSmall') },
      ).refine(
        (v: string) => Number.isNaN(Number(v)) || Number(v) <= maxTankCapacity,
        { message: t('form.oilAmountTooLarge', { max: maxTankCapacity }) },
      ),
      payableAmount: z.string().optional(),
      actualAmount: z.string().min(1, t('val.actualAmountRequired')).refine(
        (v: string) => !Number.isNaN(Number(v)) && Number(v) > 0,
        { message: t('val.actualAmountMustPositive') },
      ).refine(
        (v: string) => Number.isNaN(Number(v)) || Number(v) <= 1500,
        { message: '实付金额超过1500元，请确认是否正确' },
      ),
      oilGrade: z.string(),
      oilPrice: z.string().optional(),
    });
  }, [t]);

  const [settingsVersion, setSettingsVersion] = useState(0);
  const tankCapacityRef = useRef<number>(getTankCapacity());

  const form = useForm<RecordFormData>({
    resolver: zodResolver(koilRecordSchema),
    mode: 'onTouched',
    defaultValues: {
      reoilDate: new Date(),
      mileage: '',
      oilAmount: '',
      payableAmount: '',
      actualAmount: '',
      oilGrade: getDefaultOilGrade(),
      oilPrice: '',
    },
    context: settingsVersion,
  });

  useEffect(() => {
    const onSettingsUpdated = (): void => {
      const newCap = getTankCapacity();
      if (newCap !== tankCapacityRef.current) {
        tankCapacityRef.current = newCap;
        setSettingsVersion((v) => v + 1);
        form.trigger('oilAmount');
      }
    };
    window.addEventListener('koil-settings-updated', onSettingsUpdated);
    return () => window.removeEventListener('koil-settings-updated', onSettingsUpdated);
  }, [form]);

  const [lastRecord, setLastRecord] = useState<LastKoilRecord | null>(null);
  const [loadingLast, setLoadingLast] = useState<boolean>(true);
  const [submitSuccess, setSubmitSuccess] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const submitLockRef = useRef<boolean>(false);
  const manuallyEditedRef = useRef<Record<string, boolean>>({});
  const mileageInputRef = useRef<HTMLInputElement>(null);
  const oilAmountInputRef = useRef<HTMLInputElement>(null);
  const actualAmountInputRef = useRef<HTMLInputElement>(null);
  const payableAmountInputRef = useRef<HTMLInputElement>(null);
  const [focusedField, setFocusedField] = useState<string | null>(null);
  const duplicateCheckTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [duplicateResult, setDuplicateResult] = useState<DuplicateCheckResponse | null>(null);
  const [duplicateChecking, setDuplicateChecking] = useState(false);
  const [summaryExpanded, setSummaryExpanded] = useState(false);

  const [priceDeviationResult, setPriceDeviationResult] = useState<{
    hasDeviation: boolean;
    actualUnitPrice: number;
    referencePrice: number | null;
    referenceDate: string | null;
    deviationPercent: number | null;
    thresholdPercent: number;
    message: string | null;
  } | null>(null);
  const [priceDeviationChecking, setPriceDeviationChecking] = useState(false);
  const priceDeviationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const runPriceDeviationCheck = useCallback(async (
    province: string,
    grade: string,
    date: string,
    oilAmt: number,
    actualAmt: number,
  ): Promise<void> => {
    try {
      setPriceDeviationChecking(true);
      const result = await checkPriceDeviation({
        province,
        grade: grade as OilGrade,
        date,
        oilAmount: oilAmt,
        actualAmount: actualAmt,
      });
      setPriceDeviationResult({
        hasDeviation: result.hasDeviation,
        actualUnitPrice: result.actualUnitPrice,
        referencePrice: result.referencePrice,
        referenceDate: result.referenceDate,
        deviationPercent: result.deviationPercent,
        thresholdPercent: result.thresholdPercent,
        message: result.message,
      });
    } catch (error) {
      logger.error('单价偏差校验失败', error);
      setPriceDeviationResult(null);
    } finally {
      setPriceDeviationChecking(false);
    }
  }, []);

  const runDuplicateCheck = useCallback(async (dateStr: string, mileageVal: number) => {
    try {
      setDuplicateChecking(true);
      const result = await precheckKoilRecord({
        reoilDate: dateStr,
        mileage: mileageVal,
        oilAmount: !Number.isNaN(oilAmountNum) && oilAmountNum > 0 ? oilAmountNum : undefined,
        actualAmount: !Number.isNaN(actualAmountNum) && actualAmountNum > 0 ? actualAmountNum : undefined,
        includeBoundary: false,
        includePrevRecord: false,
      });
      setDuplicateResult(result.duplicate);
    } catch (error) {
      logger.error('防重校验请求失败', error);
      setDuplicateResult(null);
    } finally {
      setDuplicateChecking(false);
    }
  }, []);

  const { state: formLocState, requestLocation: formRequestLocation, grantPermission: formGrantPermission, denyPermission: formDenyPermission, resetPermission: formResetPermission, setManualProvince: formSetManualProvince } =
    useLocationPermission();
  const { state: displayLocState } = useLocationPermission();

  const [provinces, setProvinces] = useState<ProvinceInfo[]>([]);
  const [formOilPriceData, setFormOilPriceData] = useState<OilPriceInfo[]>([]);
  const [formOilPriceLoading, setFormOilPriceLoading] = useState<boolean>(false);
  const [formOilPriceError, setFormOilPriceError] = useState<string>('');
  const [formOilPriceUpdateDate, setFormOilPriceUpdateDate] = useState<string>('');
  const [formOilPriceSource, setFormOilPriceSource] = useState<string>('');
  const [formOilNextAdjustment, setFormOilNextAdjustment] = useState<string | undefined>();
  const [formOilPriceForecast, setFormOilPriceForecast] = useState<string | undefined>();

  const [displayManualOverride, setDisplayManualProvinceOverride] = useState<string | null>(null);
  const displayProvince = displayManualOverride || displayLocState.province;
  const displayProvinceSource = displayManualOverride
    ? '手动选择'
    : displayLocState.provinceSource;
  const [displayOilPriceCache, setDisplayOilPriceCache] = useState<Map<string, OilPriceResponse>>(() =>
    loadOilPriceCacheFromStorage(),
  );

  const initialCachedResponse = displayOilPriceCache.get(displayProvince);
  const [displayOilPriceData, setDisplayOilPriceData] = useState<OilPriceInfo[]>(() =>
    initialCachedResponse?.success && initialCachedResponse.data ? initialCachedResponse.data : [],
  );
  const [displayOilPriceLoading, setDisplayOilPriceLoading] = useState<boolean>(false);
  const [displayOilPriceError, setDisplayOilPriceError] = useState<string>('');
  const [displayOilPriceUpdateDate, setDisplayOilPriceUpdateDate] = useState<string>(() =>
    initialCachedResponse?.updateDate ?? '',
  );
  const [displayOilPriceSource, setDisplayOilPriceSource] = useState<string>(() =>
    (initialCachedResponse?.data?.[0]?.source) ?? '',
  );
  const [displayOilNextAdjustment, setDisplayOilNextAdjustment] = useState<string | undefined>(() =>
    initialCachedResponse?.nextAdjustment,
  );
  const [displayOilPriceForecast, setDisplayOilPriceForecast] = useState<string | undefined>(() =>
    initialCachedResponse?.forecast,
  );

  const loadDisplayOilPriceCacheFromStorage = (): Map<string, OilPriceResponse> => {
    if (typeof window === 'undefined') return new Map();
    try {
      const raw = getKoilItem('oil-price-cache');
      if (!raw) return new Map();
      const parsed = JSON.parse(raw) as Record<string, { data: OilPriceResponse; ts: number }>;
      const map = new Map<string, OilPriceResponse>();
      const now = Date.now();
      const TTL = 30 * 60 * 1000;
      for (const [province, entry] of Object.entries(parsed)) {
        if (now - entry.ts < TTL) {
          map.set(province, entry.data);
        }
      }
      return map;
    } catch {
      return new Map();
    }
  };

  const persistDisplayOilPriceCache = (cache: Map<string, OilPriceResponse>): void => {
    if (typeof window === 'undefined') return;
    try {
      const obj: Record<string, { data: OilPriceResponse; ts: number }> = {};
      const now = Date.now();
      for (const [province, data] of cache.entries()) {
        obj[province] = { data, ts: now };
      }
      setKoilItem('oil-price-cache', JSON.stringify(obj));
    } catch {
      // 存储失败不影响主流程
    }
  };

  const applyOilPriceResponse = (res: OilPriceResponse): void => {
    if (res.success && res.data && res.data.length > 0) {
      setDisplayOilPriceData(res.data);
      setDisplayOilPriceUpdateDate(res.updateDate || '');
      setDisplayOilPriceSource(res.data[0].source || '');
      setDisplayOilNextAdjustment(res.nextAdjustment);
      setDisplayOilPriceForecast(res.forecast);
      setDisplayOilPriceError('');
    } else {
      setDisplayOilPriceError(res.error || t('oilPrice.fetchFailed'));
      setDisplayOilPriceData([]);
    }
  };

  const fetchDisplayOilPrice = async (province: string, { showLoading = true, revalidate = true }: { showLoading?: boolean; revalidate?: boolean } = {}): Promise<void> => {
    if (!province) return;
    const cached = displayOilPriceCache.get(province);
    if (cached) {
      applyOilPriceResponse(cached);
      if (!revalidate) return;
      try {
        const res = await getTodayOilPriceFromDb(province);
        setDisplayOilPriceCache(prev => {
          const next = new Map(prev);
          next.set(province, res);
          persistOilPriceCacheToStorage(next);
          return next;
        });
        applyOilPriceResponse(res);
      } catch (err) {
        logger.error('油价静默刷新失败', err);
      }
      return;
    }
    if (showLoading) {
      setDisplayOilPriceLoading(true);
    }
    setDisplayOilPriceError('');
    try {
      const res = await getTodayOilPriceFromDb(province);
      setDisplayOilPriceCache(prev => {
        const next = new Map(prev);
        next.set(province, res);
        persistOilPriceCacheToStorage(next);
        return next;
      });
      applyOilPriceResponse(res);
    } catch (error) {
      logger.error('获取展示油价失败', error);
      setDisplayOilPriceError(t('oilPrice.networkError'));
      if (showLoading) {
        setDisplayOilPriceData([]);
      }
    } finally {
      setDisplayOilPriceLoading(false);
    }
  };

  const handleDisplayProvinceChange = (val: string): void => {
    setDisplayManualProvinceOverride(val);
    const hasExistingData = displayOilPriceData.length > 0;
    void fetchDisplayOilPrice(val, { showLoading: !hasExistingData });
  };


  const fetchLatestRecord = async (): Promise<void> => {
    try {
      setLoadingLast(true);
      const data: LastKoilRecord | null = await getLatestKoilRecord();
      setLastRecord(data);
    } catch (error) {
      logger.error('获取最新加油记录失败', error);
    } finally {
      setLoadingLast(false);
    }
  };

  useEffect(() => {
    if (!lastRecord) return;
    if (!getFollowLastGrade()) return;
    if (lastRecord.oilGrade && !manuallyEditedRef.current.oilGrade) {
      form.setValue('oilGrade', lastRecord.oilGrade, {
        shouldDirty: false,
        shouldTouch: false,
      });
    }
  }, [lastRecord, form]);

  useEffect(() => {
    const applyDefaultGrade = (): void => {
      if (manuallyEditedRef.current.oilGrade) return;
      const defaultGrade = getDefaultOilGrade();
      if (form.getValues('oilGrade') === defaultGrade) return;
      form.setValue('oilGrade', defaultGrade, {
        shouldDirty: false,
        shouldTouch: false,
      });
    };
    const onVisibility = (): void => {
      if (document.visibilityState === 'visible') applyDefaultGrade();
    };
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('koil-preferences-changed', applyDefaultGrade);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('koil-preferences-changed', applyDefaultGrade);
    };
  }, [form, lastRecord]);

  useEffect(() => {
    const initProvinces = async (): Promise<void> => {
      try {
        const provinceList = await getProvinces();
        setProvinces(provinceList);
      } catch (error) {
        logger.error('加载省份列表失败', error);
      }
    };
    void initProvinces();
  }, []);

  useEffect(() => {
    if (provinces.length === 0) return;
    if (formLocState.permissionStatus === 'granted') {
      void formRequestLocation();
    }
  }, [provinces.length, formLocState.permissionStatus, formRequestLocation]);

  useEffect(() => {
    if (!formLocState.province) return;
    const fetchPrice = async (): Promise<void> => {
      setFormOilPriceLoading(true);
      setFormOilPriceError('');
      try {
        const res = await getTodayOilPriceFromDb(formLocState.province);
        if (res.success && res.data && res.data.length > 0) {
          setFormOilPriceData(res.data);
          setFormOilPriceUpdateDate(res.updateDate || '');
          setFormOilPriceSource(res.data[0].source || '');
          setFormOilNextAdjustment(res.nextAdjustment);
          setFormOilPriceForecast(res.forecast);
        } else {
          setFormOilPriceError(res.error || t('oilPrice.fetchFailed'));
          setFormOilPriceData([]);
        }
      } catch (error) {
        logger.error('获取油价失败', error);
        setFormOilPriceError(t('oilPrice.networkError'));
        setFormOilPriceData([]);
      } finally {
        setFormOilPriceLoading(false);
      }
    };
    void fetchPrice();
  }, [formLocState.province]);

  useEffect(() => {
    if (!displayProvince) return;
    void fetchDisplayOilPrice(displayProvince);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [displayProvince]);

  useEffect(() => {
    const onPrefChanged = (): void => {
      if (typeof window === 'undefined') return;
      if (displayProvinceSource === '默认省份' || displayProvinceSource === '默认省份（兜底）') {
        const newDefault = getKoilItem('default-province') || '北京';
        void fetchDisplayOilPrice(newDefault);
      }
    };
    window.addEventListener('koil-preferences-changed', onPrefChanged);
    return () => {
      window.removeEventListener('koil-preferences-changed', onPrefChanged);
    };
  }, [displayProvinceSource]);

  const currentOilGrade = form.watch('oilGrade') as OilGrade;

  const currentOilPrice: number | null = (() => {
    const found = formOilPriceData.find(
      (item: OilPriceInfo) => item.grade === currentOilGrade,
    );
    return found ? found.price : null;
  })();

  const currentMileageValue = form.watch('mileage');
  const currentMileageNum = Number(currentMileageValue);

  const currentReoilDateValue = form.watch('reoilDate') as Date | undefined;

  useEffect(() => {
    void fetchLatestRecord();
  }, []);

  useEffect(() => {
    const dateVal = currentReoilDateValue;
    const mileageVal = currentMileageValue;
    const mileageNum = Number(mileageVal);
    if (
      !dateVal ||
      !(dateVal instanceof Date) ||
      Number.isNaN(dateVal.getTime()) ||
      mileageVal === '' ||
      Number.isNaN(mileageNum) ||
      mileageNum <= 0
    ) {
      setDuplicateResult(null);
      return;
    }
    const dateStr = format(dateVal, 'yyyy-MM-dd');
    if (duplicateCheckTimerRef.current) {
      clearTimeout(duplicateCheckTimerRef.current);
    }
    duplicateCheckTimerRef.current = setTimeout(() => {
      void runDuplicateCheck(dateStr, mileageNum);
    }, 300);
    return () => {
      if (duplicateCheckTimerRef.current) {
        clearTimeout(duplicateCheckTimerRef.current);
      }
    };
  }, [currentReoilDateValue, currentMileageValue, runDuplicateCheck]);
  const hasValidMileage =
    currentMileageValue !== '' &&
    !Number.isNaN(currentMileageNum) &&
    lastRecord !== null &&
    currentMileageNum > lastRecord.mileage;
  const currentTravelDistance = hasValidMileage
    ? currentMileageNum - lastRecord!.mileage
    : null;

  const isMileageBackwardsError = Boolean(
    lastRecord &&
      currentMileageValue !== '' &&
      !Number.isNaN(currentMileageNum) &&
      currentMileageNum > 0 &&
      currentMileageNum <= lastRecord.mileage,
  );

  const currentOilAmount = form.watch('oilAmount');
  const currentFuelAmountNum = Number(currentOilAmount);
  const currentPayableAmount = form.watch('payableAmount');
  const currentActualAmount = form.watch('actualAmount');

  const estimatedRange =
    lastRecord?.avgOilConsumption &&
    lastRecord.avgOilConsumption > 0 &&
    !Number.isNaN(currentFuelAmountNum) &&
    currentFuelAmountNum > 0
      ? (currentFuelAmountNum / lastRecord.avgOilConsumption) * 100
      : null;

  useEffect(() => {
    if (currentOilPrice === null) return;
    const fuelNum = Number(currentOilAmount);
    if (Number.isNaN(fuelNum) || fuelNum <= 0) {
      if (currentPayableAmount !== '') {
        form.setValue('payableAmount', '', {
          shouldDirty: false,
          shouldTouch: false,
          shouldValidate: false,
        });
      }
      return;
    }
    const calculated = Number((fuelNum * currentOilPrice).toFixed(2));
    form.setValue('payableAmount', String(calculated), {
      shouldDirty: false,
      shouldTouch: false,
      shouldValidate: currentPayableAmount !== '',
    });
    form.setValue('oilPrice', String(currentOilPrice), {
      shouldDirty: false,
      shouldTouch: false,
      shouldValidate: false,
    });
  }, [currentOilAmount, currentOilPrice, form, currentPayableAmount]);



  type FormFieldKey = keyof RecordFormData;

  const handleFillLastValues = (): void => {
    if (!lastRecord) return;
    form.setValue('oilAmount', String(lastRecord.oilAmount), {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    form.setValue('actualAmount', String(lastRecord.actualAmount), {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    manuallyEditedRef.current.oilAmount = true;
    manuallyEditedRef.current.actualAmount = true;
    if (lastRecord.oilGrade) {
      form.setValue('oilGrade', lastRecord.oilGrade, {
        shouldDirty: true,
        shouldTouch: true,
      });
    }
  };

  const markFieldEdited = (field: string): void => {
    manuallyEditedRef.current[field] = true;
  };

  const handleClearField = (fieldName: 'mileage' | 'oilAmount' | 'actualAmount' | 'payableAmount'): void => {
    form.setValue(fieldName, '', {
      shouldDirty: true,
      shouldTouch: true,
      shouldValidate: true,
    });
    manuallyEditedRef.current[fieldName] = true;
    const refMap: Record<string, React.RefObject<HTMLInputElement>> = {
      mileage: mileageInputRef,
      oilAmount: oilAmountInputRef,
      actualAmount: actualAmountInputRef,
      payableAmount: payableAmountInputRef,
    };
    refMap[fieldName]?.current?.focus();
  };

  const isPayableAutoCalculated = currentOilPrice !== null && currentOilAmount !== '';

  const handleRetryOilPrice = (): void => {
    if (!formLocState.province) return;
    setFormOilPriceLoading(true);
    setFormOilPriceError('');
    void getTodayOilPriceFromDb(formLocState.province)
      .then((res) => {
        if (res.success && res.data && res.data.length > 0) {
          setFormOilPriceData(res.data);
          setFormOilPriceUpdateDate(res.updateDate || '');
          setFormOilPriceSource(res.data[0].source || '');
          setFormOilNextAdjustment(res.nextAdjustment);
          setFormOilPriceForecast(res.forecast);
        } else {
          setFormOilPriceError(res.error || t('oilPrice.fetchFailed'));
        }
      })
      .catch((error) => {
        logger.error('重试获取油价失败', error);
        setFormOilPriceError(t('oilPrice.networkError'));
      })
      .finally(() => {
        setFormOilPriceLoading(false);
      });
  };

  const mileageNum = Number(currentMileageValue);
  const oilAmountNum = Number(currentOilAmount);
  const actualAmountNum = Number(currentActualAmount);

  useEffect(() => {
    const province = formLocState.province;
    const grade = currentOilGrade;
    const dateVal = currentReoilDateValue;
    const oilAmt = oilAmountNum;
    const actualAmt = actualAmountNum;

    if (
      !province ||
      !grade ||
      !dateVal ||
      !(dateVal instanceof Date) ||
      Number.isNaN(dateVal.getTime()) ||
      Number.isNaN(oilAmt) || oilAmt <= 0 ||
      Number.isNaN(actualAmt) || actualAmt <= 0
    ) {
      setPriceDeviationResult(null);
      return;
    }

    const dateStr = format(dateVal, 'yyyy-MM-dd');
    if (priceDeviationTimerRef.current) {
      clearTimeout(priceDeviationTimerRef.current);
    }
    priceDeviationTimerRef.current = setTimeout(() => {
      void runPriceDeviationCheck(province, grade, dateStr, oilAmt, actualAmt);
    }, 400);

    return () => {
      if (priceDeviationTimerRef.current) {
        clearTimeout(priceDeviationTimerRef.current);
      }
    };
  }, [formLocState.province, currentOilGrade, currentReoilDateValue, oilAmountNum, actualAmountNum, runPriceDeviationCheck]);

  const hasFieldError = Boolean(
    form.formState.errors.mileage ||
    form.formState.errors.oilAmount ||
    form.formState.errors.actualAmount ||
    form.formState.errors.reoilDate,
  );

  const isOilAmountAbnormal = oilAmountNum > 0 && oilAmountNum > getTankCapacity();
  const isActualAmountAbnormal = actualAmountNum > 0 && actualAmountNum > 1500;

  const payableNum = Number(currentPayableAmount);
  const hasValidPayable = !Number.isNaN(payableNum) && payableNum > 0;
  const actualPayableRatio = hasValidPayable ? actualAmountNum / payableNum : null;
  const isPayableRatioInvalid =
    actualPayableRatio !== null &&
    (actualPayableRatio > 1.01 || actualPayableRatio < 0.75);
  const payableRatioErrorMessage =
    actualPayableRatio !== null && actualPayableRatio > 1.01
      ? '实付金额高于应付金额，请确认'
      : actualPayableRatio !== null && actualPayableRatio < 0.75
        ? '实付金额与应付金额差异过大（优惠超过25%），请确认'
        : '';

  const canSubmit =
    !hasFieldError &&
    !duplicateResult?.exists &&
    !isPayableRatioInvalid &&
    !isMileageBackwardsError &&
    currentMileageValue !== '' &&
    currentOilAmount !== '' &&
    currentActualAmount !== '' &&
    !Number.isNaN(mileageNum) && mileageNum > 0 &&
    !Number.isNaN(oilAmountNum) && oilAmountNum > 0 &&
    !Number.isNaN(actualAmountNum) && actualAmountNum > 0;

  const warningCount = [
    isOilAmountAbnormal,
    isActualAmountAbnormal,
  ].filter(Boolean).length;

  const isFormDirty = form.formState.isDirty;

  useEffect(() => {
    if (!isFormDirty || submitSuccess) return undefined;
    const handleBeforeUnload = (e: BeforeUnloadEvent): void => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [isFormDirty, submitSuccess]);

  const handleFormKeyDown = (e: React.KeyboardEvent<HTMLFormElement>): void => {
    if (e.nativeEvent.isComposing) return;
    if (e.key === 'Enter') {
      const target = e.target as HTMLElement;
      if (target.tagName === 'TEXTAREA') return;
      e.preventDefault();
    }
  };

  const doSubmit = useCallback(async (data: RecordFormData): Promise<void> => {
    if (submitLockRef.current || isSubmitting) return;
    submitLockRef.current = true;
    setIsSubmitting(true);

    if (!data.mileage || !data.oilAmount || !data.actualAmount) {
      toast.error(t('form.pleaseFillComplete'));
      submitLockRef.current = false;
      setIsSubmitting(false);
      return;
    }

    const dateStr = format(data.reoilDate, 'yyyy-MM-dd');
    const mileageNum = Number(data.mileage);
    const oilAmountNum = Number(data.oilAmount);
    const actualAmountNum = Number(data.actualAmount);

    try {

      if (isMileageBackwardsError) {
        toast.error(`本次加油里程必须大于上次加油里程（${lastRecord?.mileage} km）`);
        return;
      }

      const precheckResult = await precheckKoilRecord({
        reoilDate: dateStr,
        mileage: mileageNum,
        oilAmount: oilAmountNum,
        actualAmount: actualAmountNum,
        source: 'entry_page',
        includeBoundary: false,
        includePrevRecord: false,
      });
      if (precheckResult.mileageChain.hasConflict) {
        toast.error(precheckResult.mileageChain.message || '里程冲突，无法录入');
        return;
      }
      if (precheckResult.duplicate.exists) {
        toast.error(t('form.duplicateRecord'));
        return;
      }
      if (precheckResult.duplicate.similarExists) {
        setDuplicateResult(precheckResult.duplicate);
      }

      const request: CreateKoilRecordRequest = {
        reoilDate: dateStr,
        mileage: mileageNum,
        oilAmount: oilAmountNum,
        payableAmount: data.payableAmount && !Number.isNaN(Number(data.payableAmount)) && Number(data.payableAmount) > 0
          ? Number(data.payableAmount)
          : actualAmountNum,
        actualAmount: actualAmountNum,
        oilPrice: data.oilPrice ? Number(data.oilPrice) : null,
        oilGrade: (data.oilGrade as OilGrade) || null,
        province: formLocState.province || null,
        source: 'entry_page',
      };
      await createKoilRecord(request);
      toast.success(t('form.submitSuccess'), {
        action: onViewRecord
          ? {
              label: t('form.viewRecord'),
              onClick: () => onViewRecord(),
            }
          : undefined,
      });
      setSubmitSuccess(true);
      window.setTimeout(async () => {
      const submittedGrade = (data.oilGrade as OilGrade) || getDefaultOilGrade();
        const defaultGrade = getDefaultOilGrade();
        const followLast = getFollowLastGrade();
        form.reset({
          reoilDate: new Date(),
          mileage: '',
          oilAmount: '',
          payableAmount: '',
          actualAmount: '',
          oilGrade: followLast ? submittedGrade : defaultGrade,
          oilPrice: '',
        });
        manuallyEditedRef.current = {};
        setDuplicateResult(null);
        mileageInputRef.current?.focus();
        await fetchLatestRecord();
        setSubmitSuccess(false);
      }, 800);
      onSuccess();
    } catch (error) {
      logger.error('添加加油记录失败', error);
      const status = (error as { response?: { status?: number } })?.response?.status;
      let message = t('form.submitFailed');
      if (status === 409) {
        message = t('form.duplicateRecord');
      } else if (status === 401) {
        message = t('form.pleaseLogin');
      } else {
        const serverMsg = (error as { response?: { data?: { error?: { message?: string } } } })
          ?.response?.data?.error?.message;
        if (serverMsg) message = serverMsg;
      }
      toast.error(message);
    } finally {
      setIsSubmitting(false);
      submitLockRef.current = false;
    }
  }, [form, onSuccess, onViewRecord, t, isSubmitting]);

  const handleResetPermission = (): void => {
    formResetPermission();
    void formGrantPermission();
  };

  const handleRelocate = (): void => {
    void formRequestLocation();
  };

  const isFieldValid = (fieldName: 'mileage' | 'oilAmount' | 'actualAmount'): boolean => {
    const value = form.watch(fieldName);
    const error = form.formState.errors[fieldName];
    const num = Number(value);
    return value !== '' && !Number.isNaN(num) && num > 0 && !error;
  };

  const currentGradeLabel = gradeOptions.find((g) => g.value === currentOilGrade)?.label || t('grade.92');

  return (
    <>
    <Form {...form}>
      <div className="flex flex-col gap-5">

          {/* 实时油价（独立省份，仅展示） */}
          {displayOilPriceError ? (
             <div className="glass-soft flex flex-col items-center gap-3 rounded-2xl p-4 ring-1 ring-warning/20 backdrop-blur-md">
               <div className="flex items-center gap-2">
                 <AlertCircleIcon className="size-4 text-warning" />
                 <span className="text-sm font-medium text-warning-foreground">{displayOilPriceError}</span>
               </div>
               <div className="flex gap-2">
                 <button
                   type="button"
                   onClick={() => void fetchDisplayOilPrice(displayProvince)}
                   className="inline-flex items-center gap-1 rounded-full bg-primary-soft px-3 py-1 text-[11px] font-medium text-primary transition-all"
                 >
                   <RefreshCwIcon className="size-3" />
                   {t('location.retryFetch')}
                 </button>
               </div>
               <p className="text-center text-[10px] text-muted-foreground/70">
                 {t('form.priceFetchFailTip')}
               </p>
             </div>
            ) : (
             <OilPriceCard
               province={displayProvince}
               priceData={displayOilPriceData.length > 0 ? displayOilPriceData : undefined}
               loading={displayOilPriceLoading}
               updateDate={displayOilPriceUpdateDate}
               nextAdjustment={displayOilNextAdjustment}
               forecast={displayOilPriceForecast}
               source={displayOilPriceSource}
               headerRight={
                 <Select
                   value={displayProvince}
                   onValueChange={handleDisplayProvinceChange}
                   disabled={provinces.length === 0 || displayOilPriceLoading}
                 >
                   <SelectTrigger className="!h-7 min-w-0 shrink-0 rounded-full border-border-light bg-primary-soft/50 px-2 text-[10px] font-medium text-foreground shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 data-[state=open]:[&_svg]:rotate-0">
                     <div className="flex min-w-0 items-center gap-1">
                       <MapPinIcon className="size-3 shrink-0 text-primary" />
                       <span className="min-w-0 shrink truncate">{displayProvince || t('location.selectProvince')}</span>
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
               }
             />
            )}

        {/* 本次加油录入（主卡片） */}
          <div className="glass rounded-2xl p-4 transition-[background-color,border-color,box-shadow] duration-300">
           <div className="mb-5 flex flex-col gap-3 sm:mb-6">
             <div className="flex items-start justify-between gap-3">
               <div className="flex items-center gap-3">
                  <div className="flex size-11 items-center justify-center rounded-2xl gradient-primary shadow-md shadow-black/25">
                   <FuelIcon className="size-5 text-white" />
                 </div>
                 <div>
                    <h2 className="text-lg font-bold tracking-tight text-foreground sm:text-xl">
                      {t('form.currentReoil')}
                    </h2>
                    <p className="mt-0.5 text-xs text-muted-foreground sm:text-sm">
                      {t('form.currentReoilDesc')}
                    </p>
                 </div>
               </div>
               <Select
                 value={formLocState.province}
                  onValueChange={(val: string) => {
                    formSetManualProvince(val);
                  }}
                 disabled={provinces.length === 0}
               >
                  <SelectTrigger className="!h-8 min-w-0 shrink-0 rounded-full border-border-light bg-primary-soft/50 px-2.5 text-[11px] font-medium text-foreground shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 data-[state=open]:[&_svg]:rotate-0">
                    <div className="flex min-w-0 items-center gap-1">
                      <MapPinIcon className="size-3 shrink-0 text-primary" />
                       <span className="min-w-0 shrink truncate">{formLocState.province || t('location.selectProvince')}</span>
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
             </div>
             <div className="flex flex-wrap items-center justify-between gap-1.5 pl-[56px] text-[10px]">
                {formLocState.isLocating ? (
                  <span className="inline-flex items-center gap-1 text-muted-foreground">
                    <Loader2Icon className="size-3 animate-spin text-primary" />
                    {t('location.locating')}
                  </span>
                ) : formLocState.permissionStatus === 'granted' && !formLocState.locateError ? (
                  <span className="inline-flex items-center gap-1 text-success">
                    <CheckIcon className="size-3" />
                    {formLocState.provinceSource || t('location.authorized')}
                  </span>
                ) : (
                  <button
                    type="button"
                    onClick={handleResetPermission}
                    className="inline-flex items-center gap-1 text-primary transition-all"
                  >
                    <MapPinIcon className="size-3" />
                    {formLocState.provinceSource || t('location.manual')} · {t('location.retryLocate')}
                 </button>
               )}
             </div>
           </div>

          <form
            onKeyDown={handleFormKeyDown}
            className="space-y-5"
            onSubmit={(e) => {
              e.preventDefault();
            }}
          >
            {/* 上次加油紧凑引用条 */}
            {loadingLast ? (
              <div className="mb-5 flex flex-col gap-1.5 rounded-2xl bg-muted-foreground/5 px-4 py-2.5">
                <div className="flex items-center justify-between">
                  <div className="h-3 w-24 animate-pulse rounded-full bg-muted-foreground/10" />
                  <div className="h-6 w-20 animate-pulse rounded-full bg-muted-foreground/10" />
                </div>
                <div className="h-3 w-full animate-pulse rounded-full bg-muted-foreground/10" />
              </div>
            ) : lastRecord ? (
              <div className="mb-5 flex flex-col gap-1.5 rounded-2xl bg-surface-2/60 px-3.5 py-3 ring-1 ring-border sm:px-4">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex h-6 items-center gap-2">
                    <HistoryIcon className="size-3.5 text-primary/70" />
                    <span className="text-[11px] font-semibold uppercase tracking-wider text-primary/80 sm:text-xs">
                      {t('lastReoil.title')}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={handleFillLastValues}
                      className="inline-flex h-6 shrink-0 items-center gap-1 rounded-full gradient-primary px-3 text-[11px] font-semibold text-white shadow-sm shadow-black/20 transition-all duration-200 ease-[cubic-bezier(0.2,0.8,0.2,1)]"
                  >
                    <HistoryIcon className="size-3" />
                    {t('lastReoil.useLast')}
                  </button>
                </div>
                <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 pl-[22px] text-[11px] text-muted-foreground sm:text-xs">
                  <span className="tabular-nums text-foreground-secondary">
                    {lastRecord.reoilDate.slice(5)}
                  </span>
                  <span className="text-muted-foreground/40">·</span>
                  <span className="tabular-nums text-foreground-secondary">
                    {lastRecord.mileage.toFixed(0)}{t('unit.km')}
                  </span>
                  <span className="text-muted-foreground/40">·</span>
                  <span className="tabular-nums text-foreground-secondary">
                    {lastRecord.oilAmount.toFixed(0)}{t('unit.liter')}
                  </span>
                  {lastRecord.actualAmount != null && Number(lastRecord.actualAmount) > 0 && (
                    <>
                      <span className="text-muted-foreground/40">·</span>
                      <span className="tabular-nums font-medium text-primary/80">
                        ¥{Number(lastRecord.actualAmount).toFixed(2)}
                      </span>
                    </>
                  )}
                </div>
              </div>
            ) : null}

            {/* 日期 + 油号（2 列并排） */}
            <div className="grid grid-cols-2 gap-4 sm:gap-5">
              <FormField
                control={form.control}
                name="reoilDate"
                render={({ field }) => (
                  <>
                    <FormItem className="flex h-full flex-col">
                        <FormLabel className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                          <CalendarIcon className="size-3.5 text-primary" />
                         {t('form.reoilDate')}
                       </FormLabel>
                       <FormControl>
                         <div className="flex h-12 w-full items-center rounded-2xl border border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm text-foreground shadow-xs">
                           <span className="tabular-nums">
                             {format(field.value, 'yyyy-MM-dd')}
                           </span>
                         </div>
                       </FormControl>
                    <FormMessage />
                    </FormItem>
                  </>
                )}
              />

              <FormField
                control={form.control}
                name="oilGrade"
                render={({ field }) => (
                   <FormItem className="flex h-full flex-col">
                       <FormLabel className="flex items-center gap-2 text-xs font-medium text-muted-foreground sm:text-sm">
                         <span className="flex items-center gap-1.5">
                           <FuelIcon className="size-3.5 text-primary" />
                           {t('form.oilGrade')}
                         </span>
                         {currentOilPrice !== null && (
                           <span className="tabular-nums text-xs font-medium text-primary/80 sm:text-sm">
                             ¥{currentOilPrice.toFixed(2)}/{t('unit.liter')}
                           </span>
                         )}
                       </FormLabel>
                    <FormControl>
                      <Select
                        value={field.value}
                        onValueChange={field.onChange}
                      >
                           <SelectTrigger className="!h-12 shrink-0 w-full rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15">
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
                     </FormControl>
                     <FormMessage />
                   </FormItem>
                )}
              />
            </div>

          {/* 主字段：里程 + 加油量（2 列并排，大号输入） */}
          <div className="grid grid-cols-2 gap-4 sm:gap-5">
            {/* 里程 */}
            <FormField
              control={form.control}
              name="mileage"
              render={({ field, fieldState }) => {
                 const isValid = isFieldValid('mileage') && fieldState.isDirty;
                 const hasError = Boolean(fieldState.error);
                 const hasValue = field.value !== '' && field.value !== null;
                 const isFocused = focusedField === 'mileage';
                 const showClear = isFocused && hasValue;
                 const showCheck = !isFocused && isValid && !hasError && !isMileageBackwardsError;
                 return (
                    <FormItem className="flex h-full flex-col">
                       <FormLabel className="text-xs font-medium text-muted-foreground sm:text-sm">
                            <span className="inline-flex items-center gap-1.5">
                              <GaugeIcon className="size-3.5 text-primary" />
                              {t('form.mileageKm')}
                           </span>
                       </FormLabel>
                      <FormControl>
                        <div className="relative">
                          <Input
                            ref={mileageInputRef}
                            type="number"
                            inputMode="decimal"
                            step="0.01"
                            placeholder="0.00"
                            className={`h-12 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-base font-semibold tabular-nums shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${hasError ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                            {...field}
                            onFocus={() => setFocusedField('mileage')}
                            onBlur={() => setFocusedField((prev) => (prev === 'mileage' ? null : prev))}
                          />
                          {showClear && (
                             <button
                               type="button"
                               aria-label="清除"
                               onMouseDown={(e) => e.preventDefault()}
                               onClick={() => handleClearField('mileage')}
                               className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                              >
                                <XIcon className="size-4" />
                              </button>
                            )}
                            {showCheck && (
                              <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                           )}
                         </div>
                       </FormControl>
                    </FormItem>
                 );
               }}
             />

            {/* 加油量 */}
            <FormField
              control={form.control}
              name="oilAmount"
              render={({ field, fieldState }) => {
                const isValid = isFieldValid('oilAmount') && fieldState.isDirty && !isOilAmountAbnormal;
                 const hasError = Boolean(fieldState.error);
                 const hasValue = field.value !== '' && field.value !== null;
                 const isFocused = focusedField === 'oilAmount';
                 const showClear = isFocused && hasValue;
                 const showCheck = !isFocused && isValid && !hasError;
                 return (
                   <FormItem className="flex h-full flex-col">
                       <FormLabel className="text-xs font-medium text-muted-foreground sm:text-sm">
                           <span className="inline-flex items-center gap-1.5">
                             <DropletsIcon className="size-3.5 text-primary" />
                             {t('form.oilAmountL')}
                           </span>
                       </FormLabel>
                      <FormControl>
                        <div className="relative">
                          <Input
                            ref={oilAmountInputRef}
                            type="number"
                            inputMode="decimal"
                            step="0.01"
                            placeholder="0.00"
                            className={`h-12 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-base font-semibold tabular-nums shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${hasError ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                            {...field}
                            onChange={(e) => {
                              field.onChange(e);
                              markFieldEdited('oilAmount');
                            }}
                            onFocus={() => setFocusedField('oilAmount')}
                            onBlur={() => setFocusedField((prev) => (prev === 'oilAmount' ? null : prev))}
                          />
                          {showClear && (
                            <button
                              type="button"
                              aria-label="清除"
                              onMouseDown={(e) => e.preventDefault()}
                              onClick={() => handleClearField('oilAmount')}
                              className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                            >
                              <XIcon className="size-4" />
                            </button>
                          )}
                          {showCheck && (
                            <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                          )}
                        </div>
                        </FormControl>
                   </FormItem>
                );
              }}
            />
           </div>

           {/* 本次行驶距离（整行） */}
           {currentTravelDistance !== null ? (
             <div className="flex flex-wrap items-center gap-1.5 rounded-full border border-emerald-500/20 bg-emerald-500/15 px-2.5 py-1.5 text-xs font-medium text-emerald-600 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] animate-in fade-in slide-in-from-top-1 duration-300 dark:border-emerald-400/25 dark:bg-emerald-500/20 dark:text-emerald-400 sm:px-3 sm:text-sm">
                 <RouteIcon className="size-4 shrink-0" />
                 <span className="truncate">
                   {t('form.tripDistance', { value: currentTravelDistance.toFixed(0) })}
                 </span>
               </div>
              ) : null}

             {/* 里程 + 加油量 通栏提示 */}
               {(duplicateResult?.exists || (duplicateResult && !duplicateResult.exists && duplicateResult.sameDayCount > 0) || isMileageBackwardsError || isOilAmountAbnormal || (estimatedRange !== null && !isOilAmountAbnormal) || duplicateChecking || form.formState.errors.mileage || form.formState.errors.oilAmount) && (
             <div className="mt-1.5 flex flex-col space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200" style={{ animationFillMode: 'backwards' }}>
               {duplicateResult?.exists ? (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{t('form.duplicateSameDay')}</span>
                 </div>
               ) : duplicateResult && !duplicateResult.exists && duplicateResult.sameDayCount > 0 ? (
                 <div className="flex items-start gap-2 rounded-xl bg-amber-500/10 px-3 py-2 text-[12px] text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{t('form.sameDayCount', { count: duplicateResult.sameDayCount })}</span>
                 </div>
               ) : null}
               {isMileageBackwardsError && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">本次加油里程必须大于上次加油里程（{lastRecord?.mileage} km）</span>
                 </div>
               )}
               {isOilAmountAbnormal && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{t('form.oilAmountTooLarge', { max: getTankCapacity() })}</span>
                 </div>
               )}
               {estimatedRange !== null && !isOilAmountAbnormal && (
                 <div className="flex items-center gap-2 rounded-xl bg-muted-foreground/10 px-3 py-2 text-[12px] text-muted-foreground ring-1 ring-inset ring-muted-foreground/15">
                   <GaugeIcon className="size-3.5 shrink-0" />
                   <span className="leading-snug">
                     {t('form.estRange')} {estimatedRange.toFixed(0)}{t('unit.km')}
                     <span className="ml-1 text-[10px] text-muted-foreground/70 tabular-nums">
                       {t('form.estRangeByAvg', { value: lastRecord?.avgOilConsumption?.toFixed(1) ?? '' })}
                     </span>
                   </span>
                 </div>
               )}
               {duplicateChecking && (
                 <div className="flex items-center gap-2 rounded-xl bg-muted/10 px-3 py-2 text-[12px] text-muted-foreground dark:bg-muted/15">
                   <Loader2Icon className="size-3.5 shrink-0 animate-spin" />
                   <span className="leading-snug">{t('form.validating')}</span>
                 </div>
               )}
               {form.formState.errors.mileage && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{form.formState.errors.mileage.message}</span>
                 </div>
               )}
               {form.formState.errors.oilAmount && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{form.formState.errors.oilAmount.message}</span>
                 </div>
               )}
             </div>
             )}
           <div className="grid grid-cols-2 gap-4 sm:gap-5">
             {/* 应付金额 */}
             {formOilPriceError ? (
                <FormField
                  control={form.control}
                  name="payableAmount"
                  render={({ field, fieldState }) => {
                    const isValid = field.value !== '' && Number(field.value) > 0 && !fieldState.error;
                    const hasError = Boolean(fieldState.error);
                    const hasValue = field.value !== '' && field.value !== null;
                    const isFocused = focusedField === 'payableAmount';
                    const showClear = isFocused && hasValue;
                    const showCheck = !isFocused && isValid && !hasError;
                     return (
                     <FormItem className="flex h-full flex-col">
                        <FormLabel className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                          <DollarSignIcon className="size-3.5 text-primary" />
                          {t('form.payableAmount')}
                        </FormLabel>
                        <FormControl>
                          <div className="relative">
                            <Input
                              ref={payableAmountInputRef}
                              type="number"
                             inputMode="decimal"
                              step="0.01"
                              placeholder={t('form.payableAmountPlaceholder')}
                             className={`h-12 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-sm shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${hasError ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                            {...field}
                            onChange={(e) => {
                              field.onChange(e);
                              markFieldEdited('payableAmount');
                            }}
                               onFocus={() => setFocusedField('payableAmount')}
                               onBlur={() => setFocusedField((prev) => (prev === 'payableAmount' ? null : prev))}
                          />
                             {showClear && (
                               <button
                                 type="button"
                                 aria-label="清除"
                                 onMouseDown={(e) => e.preventDefault()}
                                 onClick={() => handleClearField('payableAmount')}
                                 className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                               >
                                 <XIcon className="size-4" />
                               </button>
                             )}
                             {showCheck && (
                               <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                             )}
                           </div>
                        </FormControl>
                          <div className="mt-1 text-[10px] text-warning">
                            {t('form.priceFetchFailManualTip')}
                          </div>
                        </FormItem>
                      );
                    }}
                  />
            ) : (
               <FormField
                 control={form.control}
                 name="payableAmount"
                 render={({ field, fieldState }) => {
                   const numValue = Number(field.value);
                   const isValid = field.value !== '' && !Number.isNaN(numValue) && numValue > 0 && !fieldState.error;
                   const hasValue = field.value !== '' && field.value !== null && numValue > 0;
                   const isFocused = focusedField === 'payableAmount';
                   const showClear = isFocused && hasValue;
                   const showCheck = !isFocused && isValid;
                   return (
                   <FormItem className="flex h-full flex-col">
                      <FormLabel className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground sm:text-sm">
                        <DollarSignIcon className="size-3.5 text-primary" />
                        {t('form.payableAmount')}
                      </FormLabel>
                       <FormControl>
                         <div
                           className="relative flex h-12 items-center rounded-2xl border border-border-light bg-primary-soft/30 px-3.5 pr-10 shadow-xs ring-1 ring-primary/10 cursor-text focus-within:border-primary focus-within:ring-4 focus-within:ring-primary/15 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]"
                           onClick={() => payableAmountInputRef.current?.focus()}
                         >
                           <input
                             ref={payableAmountInputRef}
                             type="text"
                             value={field.value}
                             onChange={(e) => {
                               field.onChange(e.target.value);
                               markFieldEdited('payableAmount');
                             }}
                             onFocus={() => setFocusedField('payableAmount')}
                             onBlur={() => setFocusedField((prev) => (prev === 'payableAmount' ? null : prev))}
                             className="absolute inset-0 w-full cursor-text bg-transparent text-transparent caret-transparent outline-none"
                           />
                           <span className={`truncate text-base font-semibold tabular-nums sm:text-lg transition-colors duration-300 ${hasValue ? 'text-primary' : 'text-muted-foreground/50'}`}>
                             {hasValue ? `¥${numValue.toFixed(2)}` : t('form.pendingCalc')}
                           </span>
                           {showClear && (
                             <button
                               type="button"
                               aria-label="清除"
                               onMouseDown={(e) => e.preventDefault()}
                               onClick={() => handleClearField('payableAmount')}
                               className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                             >
                               <XIcon className="size-4" />
                             </button>
                           )}
                           {showCheck && (
                             <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                           )}
                         </div>
                       </FormControl>
                    </FormItem>
                   );
                 }}
               />
            )}

             {/* 实付金额 */}
             <FormField
               control={form.control}
               name="actualAmount"
               render={({ field, fieldState }) => {
                 const isValid = isFieldValid('actualAmount') && fieldState.isDirty && !isActualAmountAbnormal;
                  const hasError = Boolean(fieldState.error);
                  const hasValue = field.value !== '' && field.value !== null;
                  const isFocused = focusedField === 'actualAmount';
                  const showClear = isFocused && hasValue;
                  const showCheck = !isFocused && isValid && !hasError;
                  return (
                    <FormItem className="flex h-full flex-col">
                       <FormLabel className="text-xs font-medium text-muted-foreground sm:text-sm">
                         <span className="inline-flex items-center gap-1">
                           <WalletIcon className="size-3.5 text-primary" />
                           {t('form.actualAmount')}
                         </span>
                      </FormLabel>
                       <FormControl>
                         <div className="relative">
                           <Input
                             ref={actualAmountInputRef}
                             type="number"
                             inputMode="decimal"
                             step="0.01"
                             placeholder="0.00"
                             className={`h-12 rounded-2xl border-border-light bg-[var(--glass-bg-strong)] px-3.5 pr-10 text-base font-semibold tabular-nums shadow-xs transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] focus:border-primary focus:ring-4 focus:ring-primary/15 ${hasError ? 'border-destructive focus:border-destructive focus:ring-destructive/15' : ''}`}
                             {...field}
                             onChange={(e) => {
                               field.onChange(e);
                               markFieldEdited('actualAmount');
                             }}
                             onFocus={() => setFocusedField('actualAmount')}
                             onBlur={() => setFocusedField((prev) => (prev === 'actualAmount' ? null : prev))}
                           />
                           {showClear && (
                             <button
                               type="button"
                               aria-label="清除"
                               onMouseDown={(e) => e.preventDefault()}
                               onClick={() => handleClearField('actualAmount')}
                               className="absolute right-2.5 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-full bg-muted/25 text-muted-foreground backdrop-blur-sm transition-all duration-200"
                             >
                               <XIcon className="size-4" />
                             </button>
                           )}
                           {showCheck && (
                             <CheckCircle2Icon className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-success" />
                           )}
                         </div>
                       </FormControl>
                 </FormItem>
                );
              }}
            />
           </div>

           {/* 实付金额综合信息块 */}
           <PaymentSummaryCard
             oilAmount={currentFuelAmountNum}
             payableAmount={payableNum > 0 ? payableNum : null}
             actualAmount={actualAmountNum > 0 ? actualAmountNum : null}
             referencePrice={priceDeviationResult?.referencePrice ?? null}
             referenceDate={priceDeviationResult?.referenceDate ?? null}
             referenceNote={
               priceDeviationResult?.hasDeviation && priceDeviationResult.message
                 ? priceDeviationResult.message
                 : null
             }
             isError={isPayableRatioInvalid}
             errorMessage={payableRatioErrorMessage || null}
           />

              {/* 应付 + 实付 通栏提示（阻断性错误） */}
              {(form.formState.errors.payableAmount || form.formState.errors.actualAmount || isActualAmountAbnormal) && (
              <div className="mt-1.5 flex flex-col space-y-1.5 animate-in fade-in slide-in-from-top-1 duration-200" style={{ animationFillMode: 'backwards' }}>
               {form.formState.errors.payableAmount && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{form.formState.errors.payableAmount.message}</span>
                 </div>
               )}
               {form.formState.errors.actualAmount && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">{form.formState.errors.actualAmount.message}</span>
                 </div>
               )}
               {isActualAmountAbnormal && (
                 <div className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-[12px] text-destructive dark:bg-destructive/15">
                   <AlertCircleIcon className="mt-[1px] size-3.5 shrink-0" />
                   <span className="leading-snug">实付金额超过1500元，请确认是否正确</span>
                 </div>
               )}
              </div>
              )}

           {/* 警告汇总区 */}
          {warningCount >= 2 && (
            <div className="rounded-2xl border border-amber-500/25 bg-amber-500/15 transition-all duration-300 dark:border-amber-400/25 dark:bg-amber-500/20">
              <button
                type="button"
                onClick={() => setSummaryExpanded((prev) => !prev)}
                className="flex w-full items-center gap-2 rounded-2xl px-3 py-2 text-left text-[11px] font-medium text-amber-600 transition-colors dark:text-amber-400"
              >
                <AlertTriangleIcon className="size-3.5 shrink-0" />
                <span className="flex-1">
                  有 {warningCount} 项需确认
                </span>
                <ChevronDownIcon className={`size-3.5 shrink-0 transition-transform duration-200 ${summaryExpanded ? 'rotate-180' : ''}`} />
              </button>
              {summaryExpanded && (
                <div className="border-t border-amber-500/20 px-3 py-2 dark:border-amber-400/20">
                  <div className="flex flex-col gap-1.5">
                    {isOilAmountAbnormal && (
                      <div className="flex items-start gap-1.5 text-[11px] text-amber-600 dark:text-amber-400">
                        <AlertCircleIcon className="mt-[1px] size-3 shrink-0" />
                        <span>{t('form.oilAmountTooLarge')}</span>
                      </div>
                    )}
                    {isActualAmountAbnormal && (
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

           {/* 提交按钮 */}
           <div className="pt-2">
            <Button
              type="button"
              size="lg"
              onClick={() => {
                void form.handleSubmit((data) => void doSubmit(data))();
              }}
              disabled={!canSubmit || isSubmitting || submitSuccess}
              className={`h-13 w-full rounded-full text-base font-medium shadow-lg shadow-black/25 transition-all duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)] [&_svg]:size-5 ${submitSuccess
                ? 'bg-emerald-500 text-white shadow-emerald-500/25'
                : isSubmitting
                  ? 'gradient-primary text-white opacity-75 cursor-not-allowed'
                   : 'gradient-primary text-white'
              }`}
            >
               {submitSuccess ? (
                  <>
                    <CheckIcon className="animate-in zoom-in-50 duration-300" />
                    {t('form.submitSuccess')}
                  </>
                ) : isSubmitting ? (
                  <>
                    <Loader2Icon className="animate-spin" />
                    {t('form.submitting')}
                  </>
                ) : (
                  <>
                    <PlusIcon />
                    {t('form.submitBtn')}
                  </>
                )}
            </Button>
          </div>
          </form>
        </div>
      </div>
    </Form>
                 <LocationPermissionDialog
                  open={formLocState.permissionStatus === 'unset' && provinces.length > 0}
                  isLocating={formLocState.isLocating}
                  locateError={formLocState.locateError}
                  onAllow={formGrantPermission}
                  onDeny={formDenyPermission}
                />
    </>
  );
}

export default RecordForm;
