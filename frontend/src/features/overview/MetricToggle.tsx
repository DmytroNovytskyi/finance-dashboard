import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'

/** Which side of the money a breakdown card is showing. */
export type Direction = 'expense' | 'income'

interface MetricToggleProps {
  direction: Direction
  onChange: (direction: Direction) => void
  /** Named in the control's accessible label, so two toggles on one page stay distinguishable. */
  label: string
}

/** Switches one breakdown card between what went out and what came in. */
export function MetricToggle({ direction, onChange, label }: MetricToggleProps) {
  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={direction}
      onChange={(_event, next: Direction | null) => {
        if (next) onChange(next)
      }}
      aria-label={label}
      sx={{ flexShrink: 0 }}
    >
      <ToggleButton value="expense" sx={{ px: 1.5, py: 0.25, textTransform: 'none', fontSize: '0.875rem' }}>
        Spend
      </ToggleButton>
      <ToggleButton value="income" sx={{ px: 1.5, py: 0.25, textTransform: 'none', fontSize: '0.875rem' }}>
        Income
      </ToggleButton>
    </ToggleButtonGroup>
  )
}
