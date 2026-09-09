import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import { AppShell } from './app/AppShell'

const OverviewPage = lazy(() => import('./features/overview/OverviewPage').then((module) => ({ default: module.OverviewPage })))
const TransactionsPage = lazy(() =>
  import('./features/transactions/TransactionsPage').then((module) => ({ default: module.TransactionsPage })),
)
const CategorizePage = lazy(() => import('./features/categorize/CategorizePage').then((module) => ({ default: module.CategorizePage })))
const ImportPage = lazy(() => import('./features/import/ImportPage').then((module) => ({ default: module.ImportPage })))

/** Declarative route table; pages are lazy-loaded so charts stay out of the initial bundle. */
export function AppRoutes() {
  return (
    <Suspense
      fallback={
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 12 }}>
          <CircularProgress />
        </Box>
      }
    >
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<OverviewPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="categorize" element={<CategorizePage />} />
          <Route path="import" element={<ImportPage />} />
        </Route>
      </Routes>
    </Suspense>
  )
}
