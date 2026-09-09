import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

export interface SeriesLegendItem {
  label: string
  color: string
}

/** Compact inline legend for a chart with two or more series. */
export function SeriesLegend({ items }: { items: SeriesLegendItem[] }) {
  return (
    <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'center' }}>
      {items.map((item) => (
        <Box key={item.label} sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
          <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: item.color }} />
          <Typography variant="caption" color="text.secondary">
            {item.label}
          </Typography>
        </Box>
      ))}
    </Box>
  )
}
