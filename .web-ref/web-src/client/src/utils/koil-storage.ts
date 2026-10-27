const KEY_PREFIX = 'koil-';

export function buildKoilKey(baseKey: string): string {
  return `${KEY_PREFIX}${baseKey}`;
}

export function getKoilItem(baseKey: string): string | null {
  try {
    return window.localStorage.getItem(buildKoilKey(baseKey));
  } catch {
    return null;
  }
}

export function setKoilItem(baseKey: string, value: string): void {
  try {
    window.localStorage.setItem(buildKoilKey(baseKey), value);
  } catch {
    // ignore
  }
}

export function removeKoilItem(baseKey: string): void {
  try {
    window.localStorage.removeItem(buildKoilKey(baseKey));
  } catch {
    // ignore
  }
}
