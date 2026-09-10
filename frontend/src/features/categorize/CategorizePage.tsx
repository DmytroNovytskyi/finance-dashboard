import Box from '@mui/material/Box'
import { useCategories } from '../../api/queries'
import { PageHeader, PageShell } from '../../components/PageLayout'
import { CategoryManager } from './CategoryManager'
import { MerchantDefaults } from './MerchantDefaults'
import { UncategorizedQueue } from './UncategorizedQueue'

/** Tag uncategorized transactions, manage categories, and set merchant defaults. */
export function CategorizePage() {
  const categories = useCategories()
  return (
    <PageShell>
      <PageHeader
        title="Categorize"
        subtitle="Assign spending groups so the overview breakdown is meaningful."
      />
      <Box
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', lg: 'row' },
          gap: 2,
          flex: { xs: 'none', lg: 1 },
          minHeight: 0,
        }}
      >
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            flex: { xs: 'none', lg: '0 0 32%' },
            minWidth: 0,
            minHeight: 0,
            overflowY: 'auto',
          }}
        >
          <CategoryManager categories={categories} />
          <MerchantDefaults categories={categories} />
        </Box>
        <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, minHeight: 0 }}>
          <UncategorizedQueue categories={categories} />
        </Box>
      </Box>
    </PageShell>
  )
}
