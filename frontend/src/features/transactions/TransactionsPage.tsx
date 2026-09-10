import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import DeleteSweep from '@mui/icons-material/DeleteSweep'
import LinearProgress from '@mui/material/LinearProgress'
import Snackbar from '@mui/material/Snackbar'
import {
  useAccounts,
  useAccountsById,
  useCategories,
  useTransferSuggestions,
} from '../../api/queries'
import { transactionsApi, transfersApi } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'
import type { AccountPresentation } from '../../api/queries'
import type { TransferSuggestion } from '../../types'
import { PageHeader, PageShell } from '../../components/PageLayout'
import { useCategorizeOne } from '../categorize/hooks'
import { DeleteRangeDialog } from './DeleteRangeDialog'
import { TransactionFilters } from './TransactionFilters'
import { TransactionTable } from './TransactionTable'
import { SuggestionsPanel } from './SuggestionsPanel'
import {
  DEFAULT_TRANSACTION_SORT,
  emptyFilters,
  filtersFromUrl,
  toListParams,
  type TransactionSort,
  type TxFilters,
} from './model'

const DEFAULT_PAGE_SIZE = 50
/** Row cap for a single list request; the table sorts and paginates this full set in the browser. */
const FULL_LIST_SIZE = 10_000

/** Transactions list with filters, overview drill-down, and delete-by-range. */
export function TransactionsPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [filters, setFilters] = useState<TxFilters>(() => filtersFromUrl(searchParams))
  const [sort, setSort] = useState<TransactionSort>(DEFAULT_TRANSACTION_SORT)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)
  const [appliedQ, setAppliedQ] = useState('')
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [snackbar, setSnackbar] = useState<string | null>(null)

  useEffect(() => {
    setFilters(filtersFromUrl(searchParams))
  }, [searchParams])

  useEffect(() => {
    const timer = setTimeout(() => setAppliedQ(filters.q), 350)
    return () => clearTimeout(timer)
  }, [filters.q])

  const accountsQuery = useAccounts()
  const accounts = (accountsQuery.data ?? []) as AccountPresentation[]
  const accountsById = useAccountsById()
  const categories = useCategories()

  const changeSort = (next: TransactionSort) => {
    setSort(next)
    setPage(0)
  }

  const queryParams = useMemo(
    () => toListParams({ ...filters, q: appliedQ }, 0, FULL_LIST_SIZE, sort),
    [filters, appliedQ, sort],
  )
  const listQuery = useQuery({
    queryKey: queryKeys.transactions.list(queryParams),
    queryFn: () => transactionsApi.list(queryParams),
    placeholderData: keepPreviousData,
  })

  const suggestionsQuery = useTransferSuggestions()
  const suggestionIds = useMemo(() => {
    const ids = new Set<number>()
    for (const suggestion of suggestionsQuery.data ?? []) {
      ids.add(suggestion.fromTransactionId)
      ids.add(suggestion.toTransactionId)
    }
    return ids
  }, [suggestionsQuery.data])

  const categorizeOne = useCategorizeOne()

  const invalidateAfterTransferAction = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.transfers })
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
  }
  const applyAllMutation = useMutation({
    mutationFn: () => transfersApi.applyAll(),
    onSuccess: invalidateAfterTransferAction,
  })
  const applyOneMutation = useMutation({
    mutationFn: (suggestion: TransferSuggestion) =>
      transfersApi.pair(suggestion.fromTransactionId, suggestion.toTransactionId),
    onSuccess: invalidateAfterTransferAction,
  })
  const unlinkMutation = useMutation({
    mutationFn: (id: number) => transfersApi.unlink(id),
    onSuccess: invalidateAfterTransferAction,
  })

  const changeCategory = (id: number, categoryId: number | null) => categorizeOne.mutate({ id, categoryId })

  const applyFilters = (next: TxFilters) => {
    setFilters(next)
    setPage(0)
  }

  const clearFilters = () => {
    setFilters(emptyFilters)
    setPage(0)
  }

  const deleteMutation = useMutation({
    mutationFn: ({ from, to, accountId }: { from: string; to: string; accountId?: number }) =>
      transactionsApi.deleteRange(from, to, accountId),
    onSuccess: (_result, variables) => {
      setDeleteOpen(false)
      setSnackbar(`Deleted transactions ${variables.from} – ${variables.to}.`)
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
      queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
      queryClient.invalidateQueries({ queryKey: queryKeys.statements })
    },
  })

  return (
    <PageShell>
      <PageHeader
        title="Transactions"
        subtitle="Filter, review, and delete by range."
        action={
          <Button
            variant="outlined"
            color="error"
            startIcon={<DeleteSweep />}
            onClick={() => setDeleteOpen(true)}
          >
            Delete by range
          </Button>
        }
      />

      {(suggestionsQuery.data?.length ?? 0) > 0 ? (
        <SuggestionsPanel
          suggestions={suggestionsQuery.data ?? []}
          accounts={accountsById}
          busy={applyAllMutation.isPending || applyOneMutation.isPending}
          onApplyAll={() => applyAllMutation.mutate()}
          onApply={(suggestion) => applyOneMutation.mutate(suggestion)}
        />
      ) : null}

      <TransactionFilters filters={filters} accounts={accounts} categories={categories} onChange={applyFilters} onClear={clearFilters} />

      {listQuery.isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 320 }}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <Box sx={{ height: 4 }}>
            {listQuery.isFetching ? <LinearProgress sx={{ borderRadius: 1 }} /> : null}
          </Box>

          <TransactionTable
            data={listQuery.data}
            accounts={accountsById}
            categories={categories}
            sort={sort}
            onSortChange={changeSort}
            page={page}
            size={size}
            onPageChange={setPage}
            onSizeChange={(nextSize) => {
              setSize(nextSize)
              setPage(0)
            }}
            suggestionIds={suggestionIds}
            onCategoryChange={changeCategory}
            onUnlink={(id) => unlinkMutation.mutate(id)}
          />
        </>
      )}

      <DeleteRangeDialog
        open={deleteOpen}
        busy={deleteMutation.isPending}
        error={deleteMutation.isError && deleteMutation.error ? deleteMutation.error.message : null}
        accounts={accounts}
        onClose={() => setDeleteOpen(false)}
        onConfirm={(from, to, accountId) => deleteMutation.mutate({ from, to, accountId })}
      />

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
      />
    </PageShell>
  )
}
