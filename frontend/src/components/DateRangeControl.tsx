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
 * The years the imported statements actually reach, newest first. A fixed window would list years
 * holding nothing and let the picker send you to an empty month page, so the list comes from what
 * the accounts cover instead.
 */
function yearsOfCoverage(coverage: StatementCoverage[]): number[] {
  const starts = coverage.map((row) => row.earliestPeriodStart).filter((date): date is string => date !== null)
  const ends = coverage.map((row) => row.latestPeriodEnd).filter((date): date is string => date !== null)
  if (starts.length === 0 || ends.length === 0) {
    return []
  }
  const first = Number(starts.reduce((a, b) => (a < b ? a : b)).slice(0, 4))
  const last = Number(ends.reduce((a, b) => (a > b ? a : b)).slice(0, 4))
  const years: number[] = []
  for (let year = last; year >= first; year--) {
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

interface DateRangeControlProps {
  mode: DateMode
  range: DateRange
  onChange: (mode: DateMode, range: DateRange) => void
}

/**
 * Picks a period either by the calendar or as an explicit day range, chosen with the toggle on the
 * left. Month mode is a year and a month: choosing a month takes that month of the chosen year, and
 * choosing "All months" takes the whole of it. Dates mode is two date fields. Switching modes
 * rewrites the period in the terms of the new mode, so the two views never disagree about what is
 * selected.
 */
export function DateRangeControl({ mode, range, onChange }: DateRangeControlProps) {
  const now = new Date()
  const month = wholeMonthOf(range)
  const selectedYear = wholeYearOf(range) ?? monthIndex(range.from)?.year ?? now.getFullYear()
  const selectedMonth = month?.slice(5) ?? ALL_MONTHS

  const coverage = useStatementCoverage()
  const years = yearsOfCoverage(coverage.data ?? [])
  if (!years.includes(selectedYear)) years.push(selectedYear)
  years.sort((a, b) => b - a)

  const pickYear = (year: number) =>
    onChange('month', selectedMonth === ALL_MONTHS ? yearRange(year) : monthRange(year, Number(selectedMonth) - 1))

  const pickMonth = (value: string) =>
    onChange(
      'month',
      value === ALL_MONTHS ? yearRange(selectedYear) : monthRange(selectedYear, Number(value) - 1),
    )

  /**
   * Leaving Month mode keeps the range, which reads the same either way. Entering it writes the
   * range as the two selects can show it — see {@link monthViewOf}.
   */
  const switchMode = (next: DateMode | null) => {
    if (!next || next === mode) return
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
        value={mode}
        onChange={(_event, next: DateMode | null) => switchMode(next)}
        aria-label="How to pick the period"
        sx={{ flexShrink: 0 }}
      >
        <ToggleButton value="month" sx={{ px: 1.5, py: 0.5, textTransform: 'none' }}>
          Month
        </ToggleButton>
        <ToggleButton value="dates" sx={{ px: 1.5, py: 0.5, textTransform: 'none' }}>
          Dates
        </ToggleButton>
      </ToggleButtonGroup>

      {mode === 'month' ? (
        <>
          <TextField
            select
            size="small"
            label="Year"
            value={String(selectedYear)}
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
            value={range.from ?? ''}
            onChange={(event) => onChange('dates', { ...range, from: event.target.value || null })}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: FIELD_WIDTH }}
          />
          <TextField
            type="date"
            size="small"
            label="To"
            value={range.to ?? ''}
            onChange={(event) => onChange('dates', { ...range, to: event.target.value || null })}
            slotProps={{ inputLabel: { shrink: true } }}
            sx={{ width: FIELD_WIDTH }}
          />
        </>
      )}
    </Box>
  )
}
