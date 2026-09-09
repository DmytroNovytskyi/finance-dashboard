import type { ReactNode } from 'react'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'

interface ChartCardProps {
  title: string
  subtitle?: string
  action?: ReactNode
  /** Fixed height reserved for the chart surface (includes room for the axes). */
  chartHeight?: number
  children: ReactNode
}

/** Outlined card that frames a chart title above a fixed-height plot area. */
export function ChartCard({ title, subtitle, action, chartHeight = 260, children }: ChartCardProps) {
  return (
    <Paper
      variant="outlined"
      sx={{ p: 2.5, borderRadius: 3, height: '100%', display: 'flex', flexDirection: 'column', gap: 0.5 }}
    >
      <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2 }}>
        <Box>
          <Typography variant="h6" component="h3">
            {title}
          </Typography>
          {subtitle ? (
            <Typography variant="body2" color="text.secondary">
              {subtitle}
            </Typography>
          ) : null}
        </Box>
        {action}
      </Box>
      <Box sx={{ flex: 1, minHeight: chartHeight, pt: 1 }}>{children}</Box>
    </Paper>
  )
}
