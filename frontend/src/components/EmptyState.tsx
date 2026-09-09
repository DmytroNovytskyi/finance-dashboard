import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

interface EmptyStateProps {
  title: string
  hint?: ReactNode
}

/** Centered placeholder used inside a card when there is nothing to plot. */
export function EmptyState({ title, hint }: EmptyStateProps) {
  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 0.5, textAlign: 'center', px: 3 }}>
      <Typography variant="body1" color="text.secondary">
        {title}
      </Typography>
      {hint ? (
        <Typography variant="body2" color="text.disabled">
          {hint}
        </Typography>
      ) : null}
    </Box>
  )
}
