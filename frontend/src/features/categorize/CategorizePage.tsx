import Box from '@mui/material/Box'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import { useCategories } from '../../api/queries'
import { CategoryManager } from './CategoryManager'
import { MerchantDefaults } from './MerchantDefaults'
import { UncategorizedQueue } from './UncategorizedQueue'

/** Tag uncategorized transactions, manage categories, and set merchant defaults. */
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
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <CategoryManager categories={categories} />
            <MerchantDefaults categories={categories} />
          </Box>
        </Grid>
        <Grid size={{ xs: 12, lg: 8 }}>
          <UncategorizedQueue categories={categories} />
        </Grid>
      </Grid>
    </Box>
  )
}
