const moneyCache = new Map<string, Intl.NumberFormat>()

function moneyFormat(currency: string): Intl.NumberFormat {
  let format = moneyCache.get(currency)
  if (!format) {
    format = new Intl.NumberFormat('pl-PL', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })
    moneyCache.set(currency, format)
  }
  return format
}

/** Formats a signed amount in its currency, e.g. "-1 234,50 PLN". */
export function formatMoney(amount: number, currency: string): string {
  return moneyFormat(currency).format(amount)
}

/** Formats an amount as a positive magnitude, e.g. for KPI tiles and chart labels. */
export function formatMoneyMagnitude(amount: number, currency: string): string {
  return formatMoney(Math.abs(amount), currency)
}

const intCache = new Map<string, Intl.NumberFormat>()

function intFormat(): Intl.NumberFormat {
  let format = intCache.get('default')
  if (!format) {
    format = new Intl.NumberFormat('pl-PL')
    intCache.set('default', format)
  }
  return format
}

/** Formats an integer with thousands grouping, e.g. "1 234". */
export function formatInteger(value: number): string {
  return intFormat().format(value)
}

const dateFormatter = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })

/** Formats an ISO date like "2026-01-05" as "5 Jan 2026". */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return dateFormatter.format(new Date(year, month - 1, day))
}

const monthFormatter = new Intl.DateTimeFormat('en-GB', { month: 'short', year: 'numeric' })

/** Formats a "YYYY-MM" month key as "Mar 2026". */
export function formatMonthKey(key: string): string {
  const [year, month] = key.split('-').map(Number)
  return monthFormatter.format(new Date(year, month - 1, 1))
}

const dateTimeFormatter = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

/** Formats an ISO instant (from the backend) for display, e.g. for import times. */
export function formatDateTime(isoInstant: string): string {
  return dateTimeFormatter.format(new Date(isoInstant))
}
