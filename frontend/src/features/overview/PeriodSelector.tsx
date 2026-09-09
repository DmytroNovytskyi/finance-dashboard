import Box from '@mui/material/Box'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { DATE_RANGE_PRESETS, rangeForPreset, type DateRange, type DateRangePreset } from '../../lib/date'
import { formatDate } from '../../lib/format'

interface PeriodSelectorProps {
  preset: DateRangePreset
  range: DateRange
  onChange: (preset: DateRangePreset, range: DateRange) => void
}

function describeRange(range: DateRange): string {
  if (!range.from && !range.to) return 'All time'
  const from = range.from ? formatDate(range.from) : 'start'
  const to = range.to ? formatDate(range.to) : 'today'
  return `${from} – ${to}`
}

/** Row that selects the statistics period: a preset or an explicit custom range. */
export function PeriodSelector({ preset, range, onChange }: PeriodSelectorProps) {
  const custom = preset === 'custom'
  const pickPreset = (value: DateRangePreset) => {
    if (value === 'custom') {
      const seed = range.from ? range : rangeForPreset('thisYear')
      onChange('custom', seed)
    } else {
      onChange(value, rangeForPreset(value))
    }
  }
  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
      <TextField
        select
        size="small"
        label="Period"
        value={preset}
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
      {custom ? (
        <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
          <TextField
            type="date"
            size="small"
            label="From"
            value={range.from ?? ''}
            onChange={(event) => onChange('custom', { ...range, from: event.target.value || null })}
          />
          <TextField
            type="date"
            size="small"
            label="To"
            value={range.to ?? ''}
            onChange={(event) => onChange('custom', { ...range, to: event.target.value || null })}
          />
        </Box>
      ) : (
        <Typography variant="body2" color="text.secondary">
          {describeRange(range)}
        </Typography>
      )}
    </Paper>
  )
}
