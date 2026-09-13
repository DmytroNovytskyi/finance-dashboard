import Box from '@mui/material/Box'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import { useStatementCoverage } from '../api/queries'
import {
  ALL_MONTHS,
  MONTH_NAMES,
  monthIndex,
  monthRange,
  monthValue,
  wholeMonthOf,
  wholeYearOf,
  yearRange,
  type DateMode,
  type DateRange,
} from '../lib/date'
import type { StatementCoverage } from '../types'

/**
 * Width of one field. Both modes put two fields side by side, so giving every field the same width
 * keeps them on the same edges: switching between Month and Dates redraws the control in place
 * instead of reflowing the bar it sits in.
 */
const FIELD_WIDTH = 150

/**
 * The span the imported statements cover, earliest start to latest end across every account, or
 * null when nothing is imported. An unbounded period reaches exactly this, which is why the control
 * can name its dates where the period itself names none.
 */
function spanOfCoverage(coverage: StatementCoverage[]): DateRange | null {
  const starts = coverage.map((row) => row.earliestPeriodStart).filter((date): date is string => date !== null)
  const ends = coverage.map((row) => row.latestPeriodEnd).filter((date): date is string => date !== null)
  if (starts.length === 0 || ends.length === 0) {
    return null
  }
  return {
    from: starts.reduce((a, b) => (a < b ? a : b)),
    to: ends.reduce((a, b) => (a > b ? a : b)),
  }
}

/**
 * The years the imported statements actually reach, newest first. A fixed window would list years
 * holding nothing and let the picker send you to an empty month page, so the list comes from what
 * the accounts cover instead.
 */
function yearsOfCoverage(coverage: StatementCoverage[]): number[] {
  const span = spanOfCoverage(coverage)
  if (span?.from == null || span.to == null) {
    return []
  }
  const years: number[] = []
  for (let year = Number(span.to.slice(0, 4)); year >= Number(span.from.slice(0, 4)); year--) {
    years.push(year)
  }
  return years
}

/**
 * The period as Month mode writes it. One whole month and one whole year are spelled the same in
 * both modes and are kept as they are; a partial month or a span of several snaps to the month
 * holding its start, and a range that names no date at all snaps to the whole of the given year,
 * which is the only period the two selects can show without claiming a month or a year the range
 * does not actually cover.
 */
function monthViewOf(range: DateRange, fallbackYear: number): DateRange {
  if (wholeMonthOf(range) !== null || wholeYearOf(range) !== null) {
    return range
  }
  const start = monthIndex(range.from)
  return start ? monthRange(start.year, start.month) : yearRange(fallbackYear)
}

/** The label each mode carries on the toggle, in the order the toggle offers them. */
const MODE_LABELS: { value: DateMode; label: string }[] = [
  { value: 'allTime', label: 'All time' },
  { value: 'month', label: 'Month' },
  { value: 'dates', label: 'Dates' },
]

/**
 * The modes a control offers when its caller does not say. All-time is left out, because a page
 * that has a period preset already carries it there and two controls for one span would disagree.
 */
const DEFAULT_MODES: DateMode[] = ['month', 'dates']

interface DateRangeControlProps {
  mode: DateMode
  range: DateRange
  onChange: (mode: DateMode, range: DateRange) => void
  /** Which modes the toggle offers, in order. Defaults to every mode but all-time. */
  modes?: DateMode[]
  /**
   * Renders the fields read-only, for a caller whose period is chosen on another control. The range
   * still shows what that control set — it is greyed, not emptied — so the dates stay legible while
   * the period dropdown is the thing being driven.
   */
  disabled?: boolean
  /**
   * Shows the span the imported statements cover in place of a range that names no dates, so an
   * unbounded period can still say what it reaches instead of showing two blank fields. Display
   * only — it never becomes the range the caller holds. Leave it off on an editable control, whose
   * empty fields must stay empty rather than name a period it is not set to.
   */
  spanWhenUnbounded?: boolean
}

/**
 * Picks a period by the calendar, as an explicit day range, or not at all, chosen with the toggle
 * on the left. Month mode is a year and a month: choosing a month takes that month of the chosen
 * year, and choosing "All months" takes the whole of it. Dates mode is two date fields. All-time
 * is the absence of a period, so its month picker stays in place but inactive rather than
 * disappearing — the same two fields either way, so changing mode never reflows the bar it sits
 * in. Switching modes rewrites the period in the terms of the new mode, so the views never
 * disagree about what is selected.
 */
export function DateRangeControl({
  mode,
  range,
  onChange,
  modes = DEFAULT_MODES,
  disabled = false,
  spanWhenUnbounded = false,
}: DateRangeControlProps) {
  const now = new Date()
  const offered = MODE_LABELS.filter((option) => modes.includes(option.value))
  const active = offered.some((option) => option.value === mode) ? mode : offered[0].value
  const month = wholeMonthOf(range)
  const selectedYear = wholeYearOf(range) ?? monthIndex(range.from)?.year ?? now.getFullYear()
  const selectedMonth = month?.slice(5) ?? ALL_MONTHS

  const coverage = useStatementCoverage()
  const years = yearsOfCoverage(coverage.data ?? [])
  if (!years.includes(selectedYear)) years.push(selectedYear)
  years.sort((a, b) => b - a)

  const named = range.from !== null || range.to !== null
  const shown = spanWhenUnbounded && !named ? (spanOfCoverage(coverage.data ?? []) ?? range) : range

  const pickYear = (year: number) =>
    onChange('month', selectedMonth === ALL_MONTHS ? yearRange(year) : monthRange(year, Number(selectedMonth) - 1))

  const pickMonth = (value: string) =>
    onChange(
      'month',
      value === ALL_MONTHS ? yearRange(selectedYear) : monthRange(selectedYear, Number(value) - 1),
    )

  /**
   * Leaving Month mode keeps the range, which reads the same either way. Entering it writes the
   * range as the two selects can show it — see {@link monthViewOf}. All-time has no range to keep,
   * so it clears both edges; the other modes then start from an unbounded period, which Month
   * writes as the whole of the current year.
   */
  const switchMode = (next: DateMode | null) => {
    if (!next || next === mode) return
    if (next === 'allTime') {
      onChange('allTime', { from: null, to: null })
      return
    }
    if (next === 'dates') {
      onChange('dates', range)
      return
    }
    onChange('month', monthViewOf(range, now.getFullYear()))
  }

  return (
    <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
      <ToggleButtonGroup
        size="small"
        exclusive
        value={active}
        onChange={(_event, next: DateMode | null) => switchMode(next)}
        aria-label="How to pick the period"
        sx={{ flexShrink: 0 }}
      >
        {offered.map((option) => (
          <ToggleButton key={option.value} value={option.value} sx={{ px: 1.5, py: 0.5, textTransform: 'none' }}>
            {option.label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>

      {active !== 'dates' ? (
        <>
          <TextField
            select
            size="small"
            label="Year"
            value={String(selectedYear)}
            disabled={disabled || active !== 'month'}
            onChange={(event) => pickYear(Number(event.target.value))}
            sx={{ width: FIELD_WIDTH }}
          >
            {years.map((year) => (
              <MenuItem key={year} value={String(year)}>
                {year}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            label="Month"
            value={selectedMonth}
            disabled={disabled || active !== 'month'}
            onChange={(event) => pickMonth(event.target.value)}
            sx={{ width: FIELD_WIDTH }}
          >
            <MenuItem value={ALL_MONTHS}>All months</MenuItem>
            {MONTH_NAMES.map((name, index) => (
              <MenuItem key={name} value={monthValue(index)}>
                {name}
              </MenuItem>
            ))}
          </TextField>
        </>
      ) : (
        <>
          <TextField
            type="date"
            size="small"
            label="From"
            value={shown.from ?? ''}
            disabled={disabled}
            onChange={(event) => onChange('dates', { ...range, from: event.target.value || null })}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: FIELD_WIDTH }}
          />
          <TextField
            type="date"
            size="small"
            label="To"
            value={shown.to ?? ''}
            disabled={disabled}
            onChange={(event) => onChange('dates', { ...range, to: event.target.value || null })}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: FIELD_WIDTH }}
          />
        </>
      )}
    </Box>
  )
}
