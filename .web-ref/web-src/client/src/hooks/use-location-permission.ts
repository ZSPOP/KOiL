import { useCallback, useEffect, useState } from 'react';
import { logger } from '@lark-apaas/client-toolkit/logger';
import { reverseGeocode, getIpLocation } from '@client/src/api/koil-records';
import type { ReverseGeocodeResponse } from '@shared/api.interface';
import { getKoilItem, setKoilItem } from '@client/src/utils/koil-storage';

export type LocationPermissionStatus = 'unset' | 'granted' | 'denied';

export interface LocationState {
  permissionStatus: LocationPermissionStatus;
  province: string;
  provinceSource: string;
  isLocating: boolean;
  locateError: string;
}

const STORAGE_KEY = 'location-permission';
const PROVINCE_STORAGE_KEY = 'location-province';
const DEFAULT_PROVINCE_KEY = 'default-province';
const DEFAULT_FALLBACK_PROVINCE = '北京';

function readStoredPermission(): LocationPermissionStatus {
  if (typeof window === 'undefined') return 'unset';
  const stored = getKoilItem(STORAGE_KEY);
  if (stored === 'granted' || stored === 'denied') return stored;
  return 'unset';
}

function writeStoredPermission(status: LocationPermissionStatus): void {
  if (typeof window === 'undefined') return;
  setKoilItem(STORAGE_KEY, status);
}

function readStoredProvince(): { province: string; source: string } {
  if (typeof window === 'undefined') return { province: '', source: '' };
  const raw = getKoilItem(PROVINCE_STORAGE_KEY);
  if (!raw) return { province: '', source: '' };
  try {
    return JSON.parse(raw) as { province: string; source: string };
  } catch {
    return { province: '', source: '' };
  }
}

function writeStoredProvince(province: string, source: string): void {
  if (typeof window === 'undefined') return;
  setKoilItem(
    PROVINCE_STORAGE_KEY,
    JSON.stringify({ province, source }),
  );
}

function readDefaultProvince(): string {
  if (typeof window === 'undefined') return DEFAULT_FALLBACK_PROVINCE;
  return getKoilItem(DEFAULT_PROVINCE_KEY) || DEFAULT_FALLBACK_PROVINCE;
}

function hasDefaultProvince(): boolean {
  if (typeof window === 'undefined') return false;
  return getKoilItem(DEFAULT_PROVINCE_KEY) != null;
}

function writeDefaultProvince(province: string): void {
  if (typeof window === 'undefined') return;
  setKoilItem(DEFAULT_PROVINCE_KEY, province);
  window.dispatchEvent(new Event('koil-preferences-changed'));
}

export function useLocationPermission(): {
  state: LocationState;
  requestLocation: () => Promise<ReverseGeocodeResponse | null>;
  grantPermission: () => Promise<void>;
  denyPermission: () => void;
  resetPermission: () => void;
  setManualProvince: (province: string) => void;
} {
  const [permissionStatus, setPermissionStatus] =
    useState<LocationPermissionStatus>(readStoredPermission);
  const [province, setProvince] = useState<string>(() => {
    const stored = readStoredProvince();
    if (stored.province && stored.source === 'GPS 定位') return stored.province;
    const defaultProvince = readDefaultProvince();
    return defaultProvince;
  });
  const [provinceSource, setProvinceSource] = useState<string>(() => {
    const stored = readStoredProvince();
    if (stored.province && stored.source === 'GPS 定位') return stored.source;
    const defaultProvince = readDefaultProvince();
    return defaultProvince === DEFAULT_FALLBACK_PROVINCE ? '默认省份（兜底）' : '默认省份';
  });
  const [isLocating, setIsLocating] = useState<boolean>(false);
  const [locateError, setLocateError] = useState<string>('');

  const setProvinceNoPersist = useCallback((name: string, source: string): void => {
    setProvince(name);
    setProvinceSource(source);
  }, []);

  const setProvincePersist = useCallback((name: string, source: string): void => {
    setProvince(name);
    setProvinceSource(source);
    if (source === 'GPS 定位') {
      writeStoredProvince(name, source);
    }
  }, []);

  const applyIpOrDefaultFallback = useCallback(async (): Promise<void> => {
    const ipLoc = await getIpLocation().catch(() => null);
    if (ipLoc?.province) {
      setProvinceNoPersist(ipLoc.province, 'IP 归属地');
      if (!hasDefaultProvince()) {
        writeDefaultProvince(ipLoc.province);
      }
      return;
    }
    const defaultProvince = readDefaultProvince();
    const source = hasDefaultProvince()
      ? '默认省份'
      : '默认省份（兜底）';
    setProvinceNoPersist(defaultProvince, source);
  }, [setProvinceNoPersist]);

  const requestLocation = useCallback(async (): Promise<ReverseGeocodeResponse | null> => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setLocateError('当前设备不支持定位');
      return null;
    }

    setIsLocating(true);
    setLocateError('');

    return new Promise<ReverseGeocodeResponse | null>((resolve) => {
      navigator.geolocation.getCurrentPosition(
        async (position) => {
          try {
              const result = await reverseGeocode(
                position.coords.latitude,
                position.coords.longitude,
              );
              if (result.success && result.province) {
                setProvincePersist(result.province, 'GPS 定位');
                setPermissionStatus('granted');
                writeStoredPermission('granted');
                if (!hasDefaultProvince()) {
                  writeDefaultProvince(result.province);
                }
                resolve(result);
              } else {
                setLocateError(result.error || '未能解析所在省份');
                await applyIpOrDefaultFallback();
                resolve(result);
              }
            } catch (error) {
              logger.error('反向地理编码失败', error);
              setLocateError('定位解析失败，请手动选择省份');
              await applyIpOrDefaultFallback();
              resolve(null);
            } finally {
              setIsLocating(false);
            }
        },
        async (error) => {
          logger.warn('定位失败', error.message);
          setIsLocating(false);
          if (error.code === 1) {
            setLocateError('定位权限被拒绝');
            setPermissionStatus('denied');
            writeStoredPermission('denied');
          } else if (error.code === 2) {
            setLocateError('位置信息不可用');
          } else if (error.code === 3) {
            setLocateError('定位超时');
          } else {
            setLocateError(error.message || '定位失败');
          }
          await applyIpOrDefaultFallback();
          resolve(null);
        },
        { timeout: 10000, maximumAge: 600000, enableHighAccuracy: false },
      );
    });
  }, [setProvincePersist, applyIpOrDefaultFallback]);

  const grantPermission = useCallback(async (): Promise<void> => {
    await requestLocation();
  }, [requestLocation]);

  const denyPermission = useCallback((): void => {
    setPermissionStatus('denied');
    writeStoredPermission('denied');
    setLocateError('');
  }, []);

  const resetPermission = useCallback((): void => {
    setPermissionStatus('unset');
    writeStoredPermission('unset');
    setLocateError('');
  }, []);

  const setManualProvince = useCallback(
    (name: string): void => {
      setProvincePersist(name, '手动选择');
    },
    [setProvincePersist],
  );

  // 初始化：非 GPS 来源时，立即尝试 IP 定位（优先级：IP > 默认省份）
  // 若用户尚未配置默认省份，IP 定位结果会自动写入作为初始默认省份
  // IP 失败时不写入，保持"未配置"状态，由设置页提示用户手动选择
  useEffect(() => {
    if (provinceSource === 'GPS 定位') return;
    if (typeof navigator === 'undefined') return;

    const initWithIp = async (): Promise<void> => {
      try {
        const ipLoc = await getIpLocation();
        if (ipLoc?.province) {
          setProvinceNoPersist(ipLoc.province, 'IP 归属地');
          if (!hasDefaultProvince()) {
            writeDefaultProvince(ipLoc.province);
          }
        }
      } catch (error) {
        logger.error('IP 归属地获取失败', error);
      }
    };
    void initWithIp();
  }, []);

  useEffect(() => {
    if (province || permissionStatus === 'granted') return;
    if (permissionStatus === 'denied') {
      void applyIpOrDefaultFallback();
    }
  }, [permissionStatus, province, applyIpOrDefaultFallback]);

  useEffect(() => {
    const onPreferencesChanged = (): void => {
      const defaultProvince = readDefaultProvince();
      if (provinceSource === '默认省份' || provinceSource === '默认省份（兜底）') {
        const source = defaultProvince === DEFAULT_FALLBACK_PROVINCE
          ? '默认省份（兜底）'
          : '默认省份';
        setProvinceNoPersist(defaultProvince, source);
      }
    };
    window.addEventListener('koil-preferences-changed', onPreferencesChanged);
    return () => {
      window.removeEventListener('koil-preferences-changed', onPreferencesChanged);
    };
  }, [provinceSource, setProvinceNoPersist]);

  return {
    state: {
      permissionStatus,
      province,
      provinceSource,
      isLocating,
      locateError,
    },
    requestLocation,
    grantPermission,
    denyPermission,
    resetPermission,
    setManualProvince,
  };
}
