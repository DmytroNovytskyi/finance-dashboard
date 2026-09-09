import MenuItem from '@mui/material/MenuItem'
import Select from '@mui/material/Select'
import { DISPLAY_CURRENCIES, type DisplayCurrency } from '../preferences/displayCurrency'

interface CurrencySelectProps {
  value: DisplayCurrency
  onChange: (currency: DisplayCurrency) => void
}

/** Choose the currency the statistics figures are shown in (PLN base, or USD at the stored rate). */
export function CurrencySelect({ value, onChange }: CurrencySelectProps) {
  return (
    <Select
      size="small"
      value={value}
      onChange={(event) => onChange(event.target.value as DisplayCurrency)}
      aria-label="Display currency"
      sx={{ minWidth: 96, '.MuiSelect-select': { py: 0.75 } }}
    >
      {DISPLAY_CURRENCIES.map((currency) => (
        <MenuItem key={currency} value={currency}>
          {currency}
        </MenuItem>
      ))}
    </Select>
  )
}
