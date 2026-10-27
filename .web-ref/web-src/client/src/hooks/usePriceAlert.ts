import { useCallback, useEffect, useState } from 'react';
import { KOIL_SETTINGS_KEYS } from '@client/src/utils/koil-settings';
import { getKoilItem, setKoilItem, buildKoilKey } from '@client/src/utils/koil-storage';

const STORAGE_KEY = KOIL_SETTINGS_KEYS.priceAlertEnabled;
const DEFAULT_VALUE = 'on';

type Listener = (enabled: boolean) => void;

const listeners = new Set<Listener>();
let currentEnabled: boolean = readPriceAlertFromStorage();

function readPriceAlertFromStorage(): boolean {
  try {
    const raw = getKoilItem(STORAGE_KEY);
    if (raw == null) return DEFAULT_VALUE === 'on';
    return raw === 'on';
  } catch {
    return DEFAULT_VALUE === 'on';
  }
}

function writePriceAlertToStorage(enabled: boolean): void {
  try {
    setKoilItem(STORAGE_KEY, enabled ? 'on' : 'off');
  } catch {
    // ignore
  }
}

function notifyListeners(enabled: boolean): void {
  for (const listener of listeners) {
    listener(enabled);
  }
}

function setPriceAlert(enabled: boolean): void {
  if (enabled === currentEnabled) return;
  currentEnabled = enabled;
  writePriceAlertToStorage(enabled);
  notifyListeners(enabled);
}

function togglePriceAlert(): void {
  setPriceAlert(!currentEnabled);
}

let storageListenerAttached = false;

function ensureStorageListener(): void {
  if (storageListenerAttached) return;
  storageListenerAttached = true;
  if (typeof window === 'undefined') return;
    window.addEventListener('storage', (e: StorageEvent) => {
    const fullKey = buildKoilKey(STORAGE_KEY);
    if (e.key !== fullKey) return;
    const enabled = e.newValue === 'on';
    if (enabled !== currentEnabled) {
      currentEnabled = enabled;
      notifyListeners(enabled);
    }
  });
}

export function usePriceAlert(): {
  enabled: boolean;
  toggle: () => void;
  setEnabled: (value: boolean) => void;
} {
  const [enabled, setEnabledState] = useState<boolean>(currentEnabled);

  useEffect(() => {
    ensureStorageListener();
    const listener = (value: boolean): void => setEnabledState(value);
    listeners.add(listener);
    if (currentEnabled !== enabled) {
      setEnabledState(currentEnabled);
    }
    return () => {
      listeners.delete(listener);
    };
  }, [enabled]);

  const setEnabled = useCallback((value: boolean): void => {
    setPriceAlert(value);
  }, []);

  const toggle = useCallback((): void => {
    togglePriceAlert();
  }, []);

  return { enabled, toggle, setEnabled };
}
