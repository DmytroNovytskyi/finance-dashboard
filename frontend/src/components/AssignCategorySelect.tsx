import Box from '@mui/material/Box'
import MenuItem from '@mui/material/MenuItem'
import Select from '@mui/material/Select'
import Typography from '@mui/material/Typography'
import type { CategoryPresentation } from '../api/queries'

/** The value standing for "no category" on a select whose options are otherwise category ids. */
const UNCATEGORIZED = '__uncategorized__'

interface AssignCategorySelectProps {
  categories: CategoryPresentation[]
  onAssign: (categoryId: number | null) => void
  /** Whether the control is offered at all: a selection it cannot be applied to disables it. */
  disabled?: boolean
  /**
   * Whether "Uncategorized" is offered alongside the categories. A bar over rows that are all
   * uncategorized already has no use for it; one over the whole list does, to clear a batch that
   * was filed wrongly.
   */
  includeUncategorized?: boolean
}

/**
 * Picks a category to apply to a whole selection. The reserved categories are left out: the backend
 * refuses them, because a row wearing one is a linked leg that no pairing flow put there. The
 * control always shows its prompt rather than holding a value, so it reads as an action to take
 * instead of a field that has been filled in.
 */
export function AssignCategorySelect({
  categories,
  onAssign,
  disabled = false,
  includeUncategorized = false,
}: AssignCategorySelectProps) {
  const assignable = categories.filter((category) => !category.system)

  return (
    <Select
      size="small"
      displayEmpty
      value=""
      disabled={disabled}
      onChange={(event) => {
        const value = String(event.target.value)
        onAssign(value === UNCATEGORIZED ? null : Number(value))
      }}
      renderValue={() => 'Assign category…'}
      sx={{ minWidth: 200 }}
      inputProps={{ 'aria-label': 'Assign a category to the selected rows' }}
    >
      {includeUncategorized ? (
        <MenuItem value={UNCATEGORIZED}>
          <Typography variant="body2" color="text.disabled">
            Uncategorized
          </Typography>
        </MenuItem>
      ) : null}
      {assignable.map((category) => (
        <MenuItem key={category.id} value={String(category.id)}>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
            <Box
              aria-hidden
              sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: category.color, display: 'inline-block' }}
            />
            {category.name}
          </Box>
        </MenuItem>
      ))}
    </Select>
  )
}
