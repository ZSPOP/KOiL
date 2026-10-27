import { getKoilItem } from './koil-storage';

export const KOIL_SETTINGS_KEYS = {
  defaultOilGrade: 'default-grade',
  defaultProvince: 'default-province',
  remindThreshold: 'remind-threshold',
  abnormalThreshold: 'abnormal-threshold',
  priceAlertEnabled: 'price-alert',
  followLastGrade: 'follow-last-grade',
  tankCapacity: 'tank-capacity',
};

export const DEFAULT_TANK_CAPACITY = 65;
export const MIN_TANK_CAPACITY = 20;
export const MAX_TANK_CAPACITY = 200;

export function getTankCapacity(): number {
  try {
    const raw = getKoilItem(KOIL_SETTINGS_KEYS.tankCapacity);
    if (raw == null) return DEFAULT_TANK_CAPACITY;
    const n = Number(raw);
    if (!Number.isFinite(n)) return DEFAULT_TANK_CAPACITY;
    return Math.max(
      MIN_TANK_CAPACITY,
      Math.min(MAX_TANK_CAPACITY, Math.round(n)),
    );
  } catch {
    return DEFAULT_TANK_CAPACITY;
  }
}
