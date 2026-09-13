import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import { DISPLAY_CURRENCIES, type DisplayCurrency } from '../features/preferences/displayCurrency'

interface CurrencySelectProps {
  value: DisplayCurrency
  onChange: (currency: DisplayCurrency) => void
}

/**
 * Choose the currency the statistics figures are shown in (PLN base, or USD at the stored rate).
 * Only the pages that report statistics offer it, because it converts reported figures rather than
 * the rows themselves — a transaction always shows the amount its own statement recorded.
 */
export function CurrencySelect({ value, onChange }: CurrencySelectProps) {
  return (
    <TextField
      select
      size="small"
      label="Currency"
      value={value}
      onChange={(event) => onChange(event.target.value as DisplayCurrency)}
      sx={{ minWidth: 120 }}
    >
      {DISPLAY_CURRENCIES.map((currency) => (
        <MenuItem key={currency} value={currency}>
          {currency}
        </MenuItem>
      ))}
    </TextField>
  )
}
