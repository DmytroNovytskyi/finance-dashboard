import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import DeleteSweep from '@mui/icons-material/DeleteSweep'
import LinearProgress from '@mui/material/LinearProgress'
import Snackbar from '@mui/material/Snackbar'
import Typography from '@mui/material/Typography'
import { useAccounts, useAccountsById, useCategories } from '../../api/queries'
import { transactionsApi } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'
import type { AccountPresentation } from '../../api/queries'
import { DeleteRangeDialog } from './DeleteRangeDialog'
import { TransactionFilters } from './TransactionFilters'
import { TransactionTable } from './TransactionTable'
import { emptyFilters, filtersFromUrl, toListParams, type TxFilters } from './model'

const DEFAULT_PAGE_SIZE = 50

/** Transactions list with filters, overview drill-down, and delete-by-range. */
export function TransactionsPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [filters, setFilters] = useState<TxFilters>(() => filtersFromUrl(searchParams))
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

  const queryParams = useMemo(
    () => toListParams({ ...filters, q: appliedQ }, page, size),
    [filters, appliedQ, page, size],
  )
  const listQuery = useQuery({
    queryKey: queryKeys.transactions.list(queryParams),
    queryFn: () => transactionsApi.list(queryParams),
  })

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
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Transactions
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Filter, review, and delete by range.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          color="error"
          startIcon={<DeleteSweep />}
          onClick={() => setDeleteOpen(true)}
        >
          Delete by range
        </Button>
      </Box>

      <TransactionFilters filters={filters} accounts={accounts} categories={categories} onChange={applyFilters} onClear={clearFilters} />

      {listQuery.isFetching ? <LinearProgress sx={{ borderRadius: 1 }} /> : null}

      <TransactionTable
        data={listQuery.data}
        accounts={accountsById}
        categories={categories}
        page={page}
        size={size}
        onPageChange={setPage}
        onSizeChange={(nextSize) => {
          setSize(nextSize)
          setPage(0)
        }}
      />

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
    </Box>
  )
}
