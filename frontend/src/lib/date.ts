/** A closed date range expressed as ISO dates; null means an unbounded edge. */
export interface DateRange {
  from: string | null
  to: string | null
}

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
