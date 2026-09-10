import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'

interface SuggestionsPanelProps<T> {
  title: string
  subtitle: string
  suggestions: T[]
  busy: boolean
  rowKey: (suggestion: T) => string
  /** The text block of one row; the Apply button is added by the panel. */
  renderRow: (suggestion: T) => ReactNode
  onApplyAll: () => void
  onApply: (suggestion: T) => void
}

/**
 * Pending auto-detected pairs of rows: review them and apply each one or all at once. Nothing is
 * linked until it is applied here.
 */
export function SuggestionsPanel<T>({
  title,
  subtitle,
  suggestions,
  busy,
  rowKey,
  renderRow,
  onApplyAll,
  onApply,
}: SuggestionsPanelProps<T>) {
  return (
    <Paper variant="outlined" sx={{ borderRadius: 3, px: 2.5, py: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h6" component="h3">
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        </Box>
        <Button variant="outlined" onClick={onApplyAll} disabled={busy}>
          Apply all
        </Button>
      </Box>
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.5,
          maxHeight: 'min(280px, 22dvh)',
          overflow: 'auto',
        }}
      >
        {suggestions.map((suggestion) => (
          <Box
            key={rowKey(suggestion)}
            sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 1, py: 0.75, borderRadius: 1.5, '&:hover': { bgcolor: 'action.hover' } }}
          >
            <Box sx={{ minWidth: 0, flexGrow: 1 }}>{renderRow(suggestion)}</Box>
            <Button size="small" onClick={() => onApply(suggestion)} disabled={busy}>
              Apply
            </Button>
          </Box>
        ))}
      </Box>
    </Paper>
  )
}
