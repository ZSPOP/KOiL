import { createContext, useContext, useEffect, useState, useCallback, useMemo, type ReactNode } from 'react';
import { translations } from '@client/src/i18n/translations';
import { getKoilItem, setKoilItem } from '@client/src/utils/koil-storage';

export type Language = 'zh-CN' | 'zh-TW' | 'en';
export type LanguageMode = 'system' | Language;

function getSystemLanguage(): Language {
  const lang = navigator.language;
  if (lang.startsWith('zh-TW') || lang.startsWith('zh-HK') || lang.startsWith('zh-Hant')) {
    return 'zh-TW';
  }
  if (lang.startsWith('zh')) {
    return 'zh-CN';
  }
  if (lang.startsWith('en')) {
    return 'en';
  }
  return 'zh-CN';
}

interface LanguageContextValue {
  mode: LanguageMode;
  language: Language;
  setMode: (mode: LanguageMode) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const LanguageContext = createContext<LanguageContextValue | null>(null);

function getStoredMode(): LanguageMode {
  const stored = getKoilItem('language-mode');
  if (stored === 'system' || stored === 'zh-CN' || stored === 'zh-TW' || stored === 'en') {
    return stored;
  }
  return 'system';
}

function translate(key: string, lang: Language, params?: Record<string, string | number>): string {
  const dict = translations[lang] ?? translations['zh-CN'];
  let text = dict[key] ?? translations['zh-CN'][key] ?? key;
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.replace(new RegExp(`\\{${k}\}`, 'g'), String(v));
    }
  }
  return text;
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<LanguageMode>(() => getStoredMode());
  const [language, setLanguage] = useState<Language>(() => {
    const stored = getStoredMode();
    return stored === 'system' ? getSystemLanguage() : stored;
  });

  const setMode = useCallback((newMode: LanguageMode) => {
    setModeState(newMode);
    setKoilItem('language-mode', newMode);
    if (newMode === 'system') {
      setLanguage(getSystemLanguage());
    } else {
      setLanguage(newMode);
    }
  }, []);

  useEffect(() => {
    if (mode !== 'system') return;
    const handler = () => setLanguage(getSystemLanguage());
    window.addEventListener('languagechange', handler);
    return () => window.removeEventListener('languagechange', handler);
  }, [mode]);

  const t = useCallback((key: string, params?: Record<string, string | number>): string => {
    return translate(key, language, params);
  }, [language]);

  const value = useMemo(() => ({ mode, language, setMode, t }), [mode, language, setMode, t]);

  return (
    <LanguageContext.Provider value={value}>
      {children}
    </LanguageContext.Provider>
  );
}

export function useTranslation(): { language: Language; mode: LanguageMode; setMode: (mode: LanguageMode) => void; t: (key: string, params?: Record<string, string | number>) => string } {
  const ctx = useContext(LanguageContext);
  if (!ctx) throw new Error('useTranslation must be used within LanguageProvider');
  return ctx;
}
