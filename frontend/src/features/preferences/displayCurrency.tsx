import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'

export const DISPLAY_CURRENCIES = ['PLN', 'USD'] as const
export type DisplayCurrency = (typeof DISPLAY_CURRENCIES)[number]

export function isDisplayCurrency(value: string): value is DisplayCurrency {
  return (DISPLAY_CURRENCIES as readonly string[]).includes(value)
}

const STORAGE_KEY = 'finance-dashboard.display-currency'
const DEFAULT_DISPLAY_CURRENCY: DisplayCurrency = 'PLN'

interface DisplayCurrencyContextValue {
  displayCurrency: DisplayCurrency
  setDisplayCurrency: (currency: DisplayCurrency) => void
}

const DisplayCurrencyContext = createContext<DisplayCurrencyContextValue>({
  displayCurrency: DEFAULT_DISPLAY_CURRENCY,
  setDisplayCurrency: () => undefined,
})

/** Shares the display currency across every statistics page and persists it in the browser. */
export function DisplayCurrencyProvider({ children }: { children: ReactNode }) {
  const [displayCurrency, setDisplayCurrency] = useState<DisplayCurrency>(() => {
    try {
      const stored = window.localStorage.getItem(STORAGE_KEY)
      if (stored && isDisplayCurrency(stored)) {
        return stored
      }
    } catch {
      /* storage unavailable: keep the default for this session only */
    }
    return DEFAULT_DISPLAY_CURRENCY
  })

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEY, displayCurrency)
    } catch {
      /* storage unavailable: keep the selection for this session only */
    }
  }, [displayCurrency])

  return (
    <DisplayCurrencyContext.Provider value={{ displayCurrency, setDisplayCurrency }}>
      {children}
    </DisplayCurrencyContext.Provider>
  )
}

export function useDisplayCurrency(): DisplayCurrencyContextValue {
  return useContext(DisplayCurrencyContext)
}
