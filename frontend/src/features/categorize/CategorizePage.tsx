import Box from '@mui/material/Box'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { useCategories } from '../../api/queries'
import { CategoryManager } from './CategoryManager'
import { UncategorizedQueue } from './UncategorizedQueue'

/** Tag uncategorized transactions and manage the spending categories. */
export function CategorizePage() {
  const categories = useCategories()
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Categorize
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Assign spending groups so the overview breakdown is meaningful.
        </Typography>
      </Box>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <CategoryManager categories={categories} />
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          <UncategorizedQueue categories={categories} />
        </Grid>
      </Grid>
    </Box>
  )
}
