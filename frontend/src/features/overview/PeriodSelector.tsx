import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import { CurrencySelect } from '../../components/CurrencySelect'
import { DateRangeControl } from '../../components/DateRangeControl'
import { DATE_RANGE_PRESETS, modeForRange, rangeForPreset, type DateRangePreset } from '../../lib/date'
import { useDisplayCurrency } from '../preferences/displayCurrency'
import {
  GRANULARITY_OPTIONS,
  type PeriodSelection,
  type TrendGranularity,
} from '../preferences/statisticsPreferences'

export type { PeriodSelection }

interface PeriodSelectorProps {
  period: PeriodSelection
  granularity: TrendGranularity
  onChange: (period: PeriodSelection) => void
  onGranularityChange: (granularity: TrendGranularity) => void
}

/**
 * The statistics toolbar, identical on the overview and the trends because every control on it
 * means the same thing on both: the period, how it is written, how charts divide it, and the
 * currency the figures are reported in. Each one is shared, so changing it on one page changes the
 * other — they are two views of one selection, not two independent screens.
 *
 * Choosing a preset moves the date control to whichever mode reads that span most naturally —
 * "Last month" is one whole month, "This year" is not — and editing the control directly drops the
 * preset, since the period is then no longer one of the named spans.
 */
export function PeriodSelector({
  period,
  granularity,
  onChange,
  onGranularityChange,
}: PeriodSelectorProps) {
  const { displayCurrency, setDisplayCurrency } = useDisplayCurrency()

  const pickPreset = (preset: DateRangePreset) => {
    if (preset === 'custom') {
      const seeded = period.range.from || period.range.to ? period.range : rangeForPreset('thisYear')
      onChange({ preset, range: seeded, mode: period.mode })
      return
    }
    const range = rangeForPreset(preset)
    onChange({ preset, range, mode: modeForRange(range) })
  }

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
      <TextField
        select
        size="small"
        label="Period"
        value={period.preset}
        onChange={(event) => pickPreset(event.target.value as DateRangePreset)}
        sx={{ minWidth: 180 }}
      >
        {DATE_RANGE_PRESETS.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
        <MenuItem value="custom">Custom range</MenuItem>
      </TextField>

      <DateRangeControl
        mode={period.mode}
        range={period.range}
        onChange={(mode, range) => onChange({ preset: 'custom', range, mode })}
      />

      <TextField
        select
        size="small"
        label="Granularity"
        value={granularity}
        onChange={(event) => onGranularityChange(event.target.value as TrendGranularity)}
        sx={{ minWidth: 160 }}
      >
        {GRANULARITY_OPTIONS.map((option) => (
          <MenuItem key={option.value} value={option.value}>
            {option.label}
          </MenuItem>
        ))}
      </TextField>

      <CurrencySelect value={displayCurrency} onChange={setDisplayCurrency} />
    </Paper>
  )
}
