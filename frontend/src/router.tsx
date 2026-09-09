import { Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { OverviewPage } from './features/overview/OverviewPage'
import { TransactionsPage } from './features/transactions/TransactionsPage'
import { CategorizePage } from './features/categorize/CategorizePage'
import { ImportPage } from './features/import/ImportPage'

/** Declarative route table; every page renders inside the {@link AppShell} layout. */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<OverviewPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route path="categorize" element={<CategorizePage />} />
        <Route path="import" element={<ImportPage />} />
      </Route>
    </Routes>
  )
}
