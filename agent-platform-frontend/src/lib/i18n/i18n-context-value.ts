import { createContext } from 'react'

export type Locale = 'en' | 'zh'

export type I18nContextValue = {
  locale: Locale
  setLocale: (locale: Locale) => void
  t: (key: string, params?: Record<string, string | number | null | undefined>) => string
  toggleLocale: () => void
}

export const I18nContext = createContext<I18nContextValue | null>(null)
