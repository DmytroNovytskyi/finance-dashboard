import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { useTheme } from '@mui/material/styles'

export interface TooltipRow {
  label: string
  value: string
  color?: string
}

interface ChartTooltipCardProps {
  title?: string | number
  rows: TooltipRow[]
}

/** MUI-styled content for Recharts tooltips, colored by theme tokens in both modes. */
export function ChartTooltipCard({ title, rows }: ChartTooltipCardProps) {
  const theme = useTheme()
  return (
    <Paper
      sx={{
        px: 1.5,
        py: 1,
        borderRadius: 2,
        border: 1,
        borderColor: 'divider',
        boxShadow: theme.shadows[4],
      }}
    >
      {title !== undefined && title !== null && title !== '' ? (
        <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
          {title}
        </Typography>
      ) : null}
      <Box component="dl" sx={{ m: 0, display: 'grid', gap: 0.25 }}>
        {rows.map((row) => (
          <Box key={row.label} sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 150 }}>
            <Box
              aria-hidden
              sx={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                bgcolor: row.color ?? 'text.primary',
                flexShrink: 0,
              }}
            />
            <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
              {row.label}
            </Typography>
            <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
              {row.value}
            </Typography>
          </Box>
        ))}
      </Box>
    </Paper>
  )
}
