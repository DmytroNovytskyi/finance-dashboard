/** A closed date range expressed as ISO dates; null means an unbounded edge. */
export interface DateRange {
  from: string | null
  to: string | null
}

/**
 * The three ways a period can be written: unbounded, as one whole calendar month or year, or as an
 * explicit range of days. All-time is the absence of a period rather than a wide one, Month is the
 * quick path and Dates is the exact one; all three describe the same underlying {@link DateRange},
 * so switching between them never loses the period.
 */
export type DateMode = 'allTime' | 'month' | 'dates'

/** The month picker's "All months" option: with the chosen year it selects that whole year. */
export const ALL_MONTHS = 'all'

/** Month names in calendar order, for the month picker. */
export const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
]

export type DateRangePreset =
  | 'thisMonth'
  | 'lastMonth'
  | 'thisQuarter'
  | 'lastQuarter'
  | 'thisYear'
  | 'allTime'
  | 'custom'

export const DATE_RANGE_PRESETS: { value: Exclude<DateRangePreset, 'custom'>; label: string }[] = [
  { value: 'thisMonth', label: 'This month' },
  { value: 'lastMonth', label: 'Last month' },
  { value: 'thisQuarter', label: 'This quarter' },
  { value: 'lastQuarter', label: 'Last quarter' },
  { value: 'thisYear', label: 'This year' },
  { value: 'allTime', label: 'All time' },
]

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function ymd(year: number, month: number, day: number): string {
  return `${year}-${pad(month)}-${pad(day)}`
}

function dayRange(start: Date, end: Date): DateRange {
  return {
    from: ymd(start.getFullYear(), start.getMonth() + 1, start.getDate()),
    to: ymd(end.getFullYear(), end.getMonth() + 1, end.getDate()),
  }
}

/** Formats a year-month like "2026-03" from a Date. */
export function monthKey(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}

/** The "YYYY-MM" an ISO date falls in, or null when there is no date. */
export function monthOf(iso: string | null | undefined): string | null {
  return iso ? iso.slice(0, 7) : null
}

/** The "01".."12" of a 0-based month index, for the month picker's option values. */
export function monthValue(month: number): string {
  return pad(month + 1)
}

/** The inclusive range covering one whole calendar month; month is 0-based. */
export function monthRange(year: number, month: number): DateRange {
  return { from: ymd(year, month + 1, 1), to: ymd(year, month + 1, lastDayOf(year, month)) }
}

/** The inclusive range covering one whole calendar year. */
export function yearRange(year: number): DateRange {
  return { from: ymd(year, 1, 1), to: ymd(year, 12, 31) }
}

/** The year and 0-based month an ISO date falls in, or null when there is no date. */
export function monthIndex(iso: string | null | undefined): { year: number; month: number } | null {
  const month = monthOf(iso)
  if (!month) return null
  const [year, index] = month.split('-').map(Number)
  if (!Number.isFinite(year) || !Number.isFinite(index)) return null
  return { year, month: index - 1 }
}

/** The "YYYY-MM" a range covers when it is exactly one whole calendar month, else null. */
export function wholeMonthOf(range: DateRange): string | null {
  const start = monthIndex(range.from)
  if (!start || !range.to) return null
  return monthRange(start.year, start.month).to === range.to
    ? `${start.year}-${monthValue(start.month)}`
    : null
}

/** The year a range covers when it is exactly one whole calendar year, else null. */
export function wholeYearOf(range: DateRange): number | null {
  if (!range.from || !range.to) return null
  const [year] = range.from.split('-')
  return range.from === `${year}-01-01` && range.to === `${year}-12-31` ? Number(year) : null
}

/**
 * The mode a range reads naturally in. Month mode writes a period as one whole calendar month or one
 * whole calendar year, so anything else — a partial month, a span of several, a range with only one
 * edge set, or no range at all — is a date range. An unbounded range names no month and no year,
 * which is why it is written with empty date fields rather than with a selection the picker would be
 * claiming on its behalf.
 */
export function modeForRange(range: DateRange): DateMode {
  const calendar = wholeMonthOf(range) !== null || wholeYearOf(range) !== null
  return calendar ? 'month' : 'dates'
}

/** Computes the inclusive range for a preset, relative to today in local time. */
export function rangeForPreset(preset: Exclude<DateRangePreset, 'custom'>, now = new Date()): DateRange {
  const year = now.getFullYear()
  const month = now.getMonth()
  switch (preset) {
    case 'thisMonth':
      return { from: ymd(year, month + 1, 1), to: ymd(year, month + 1, now.getDate()) }
    case 'lastMonth':
      return { from: ymd(year, month, 1), to: ymd(year, month, lastDayOf(year, month)) }
    case 'thisQuarter':
      return { from: ymd(year, quarterStart(month) + 1, 1), to: ymd(year, month + 1, now.getDate()) }
    case 'lastQuarter': {
      const previousStart = new Date(year, quarterStart(month) - 3, 1)
      const previousEnd = new Date(year, quarterStart(month), 0)
      return dayRange(previousStart, previousEnd)
    }
    case 'thisYear':
      return { from: `${year}-01-01`, to: ymd(year, month + 1, now.getDate()) }
    case 'allTime':
      return { from: null, to: null }
  }
}

function lastDayOf(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate()
}

/** 0-based month of the start of the quarter containing the given 0-based month. */
function quarterStart(month: number): number {
  return Math.floor(month / 3) * 3
}
