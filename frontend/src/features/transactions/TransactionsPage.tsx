import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
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
  useRefundSuggestions,
  useTransferSuggestions,
} from '../../api/queries'
import { refundsApi, transactionsApi, transfersApi } from '../../api/endpoints'
import { buildQuery } from '../../api/client'
import { queryKeys } from '../../api/keys'
import type { AccountPresentation } from '../../api/queries'
import type { RefundSuggestion, Transaction, TransferSuggestion } from '../../types'
import { PageHeader, PageShell } from '../../components/PageLayout'
import { useCategorizeOne } from '../categorize/hooks'
import { DeleteRangeDialog } from './DeleteRangeDialog'
import { LinkActions } from './LinkActions'
import { useFittingRows } from '../../hooks/useFittingRows'
import { usePageOnWheel } from '../../hooks/usePageOnWheel'
import { RefundSuggestionRow, TransferSuggestionRow } from './SuggestionRows'
import { TransactionFilters } from './TransactionFilters'
import { TABLE_ROW_HEIGHT, TransactionTable } from './TransactionTable'
import { SuggestionsPanel } from './SuggestionsPanel'
import {
  DEFAULT_TRANSACTION_SORT,
  emptyFilters,
  filtersFromUrl,
  toListParams,
  type TransactionSort,
  type TxFilters,
} from './model'

/** Page size used until the table has been measured and can say how many rows it has room for. */
const DEFAULT_PAGE_SIZE = 50

/** Height of the pagination bar, taken out of the table so the bar appearing never shifts the fit. */
const PAGINATION_HEIGHT = 52

/** Transactions list with filters, overview drill-down, and delete-by-range. */
export function TransactionsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [filters, setFilters] = useState<TxFilters>(() => filtersFromUrl(searchParams))
  const [sort, setSort] = useState<TransactionSort>(DEFAULT_TRANSACTION_SORT)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)
  const [appliedQ, setAppliedQ] = useState('')
  const [editMode, setEditMode] = useState(false)
  const [selected, setSelected] = useState<ReadonlyMap<number, Transaction>>(new Map())
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [snackbar, setSnackbar] = useState<string | null>(null)

  useEffect(() => {
    setFilters(filtersFromUrl(searchParams))
    setPage(0)
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
    () => toListParams({ ...filters, q: appliedQ }, page, size, sort),
    [filters, appliedQ, page, size, sort],
  )
  const listQuery = useQuery({
    queryKey: queryKeys.transactions.list(queryParams),
    queryFn: () => transactionsApi.list(queryParams),
    placeholderData: keepPreviousData,
  })

  const totalElements = listQuery.data?.totalElements ?? 0
  const { containerRef, container, rows: rowsPerPage, rowHeight } = useFittingRows(totalElements, {
    rowSelector: 'tbody tr',
    reservedSelector: 'thead',
    paginationSelector: '.MuiTablePagination-root',
    paginationHeight: PAGINATION_HEIGHT,
    naturalRowHeight: TABLE_ROW_HEIGHT,
  })
  const maxPage = Math.max(0, Math.ceil(totalElements / rowsPerPage) - 1)
  const shownPage = Math.min(page, maxPage)

  usePageOnWheel(container, {
    onNext: () => setPage((current) => Math.min(current + 1, maxPage)),
    onPrevious: () => setPage((current) => Math.max(current - 1, 0)),
    canNext: shownPage < maxPage && !listQuery.isFetching,
    canPrevious: shownPage > 0 && !listQuery.isFetching,
    enabled: maxPage > 0,
  })

  useEffect(() => {
    if (rowsPerPage !== size) setSize(rowsPerPage)
  }, [rowsPerPage, size])

  useEffect(() => {
    setSelected(new Map())
  }, [filters, sort, editMode])

  const pageRows = listQuery.data?.content ?? []
  const allPageSelected = pageRows.length > 0 && pageRows.every((row) => selected.has(row.id))
  const somePageSelected = pageRows.some((row) => selected.has(row.id))

  const toggleSelect = (transaction: Transaction) => {
    setSelected((current) => {
      const next = new Map(current)
      if (next.has(transaction.id)) next.delete(transaction.id)
      else next.set(transaction.id, transaction)
      return next
    })
  }

  const selectPage = (checked: boolean) => {
    setSelected((current) => {
      const next = new Map(current)
      for (const row of pageRows) {
        if (checked) next.set(row.id, row)
        else next.delete(row.id)
      }
      return next
    })
  }

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

  const refundSuggestionsQuery = useRefundSuggestions()
  const refundSuggestionIds = useMemo(() => {
    const ids = new Set<number>()
    for (const suggestion of refundSuggestionsQuery.data ?? []) {
      ids.add(suggestion.purchaseTransactionId)
      for (const refundId of suggestion.refundTransactionIds) {
        ids.add(refundId)
      }
    }
    return ids
  }, [refundSuggestionsQuery.data])

  const invalidateAfterRefundAction = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.refunds })
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
  }
  const applyAllRefundsMutation = useMutation({
    mutationFn: () => refundsApi.applyAll(),
    onSuccess: invalidateAfterRefundAction,
  })
  const applyRefundMutation = useMutation({
    mutationFn: (suggestion: RefundSuggestion) =>
      refundsApi.pair(suggestion.purchaseTransactionId, suggestion.refundTransactionIds),
    onSuccess: invalidateAfterRefundAction,
  })
  const unlinkRefundMutation = useMutation({
    mutationFn: (id: number) => refundsApi.unlink(id),
    onSuccess: invalidateAfterRefundAction,
  })

  const changeCategory = (id: number, categoryId: number | null) => categorizeOne.mutate({ id, categoryId })

  const invalidateAfterLink = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.transfers })
    queryClient.invalidateQueries({ queryKey: queryKeys.refunds })
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
  }
  const linkRefundMutation = useMutation({
    mutationFn: ({ purchaseId, refundIds }: { purchaseId: number; refundIds: number[] }) =>
      refundsApi.pair(purchaseId, refundIds),
    onSuccess: (result) => {
      invalidateAfterLink()
      setSelected(new Map())
      setSnackbar(`Linked ${result.refunds.length + 1} transactions as a refund.`)
    },
    onError: (error: Error) => setSnackbar(error.message),
  })
  const linkTransferMutation = useMutation({
    mutationFn: ({ fromId, toId }: { fromId: number; toId: number }) => transfersApi.pair(fromId, toId),
    onSuccess: () => {
      invalidateAfterLink()
      setSelected(new Map())
      setSnackbar('Linked the two transactions as an internal transfer.')
    },
    onError: (error: Error) => setSnackbar(error.message),
  })

  /**
   * Shows exactly the given rows. The filters are set here as well as written to the URL: the list
   * only re-reads the URL when it changes, so navigating alone does nothing once the filters have
   * been edited by hand — the address bar still holds the target and looks unchanged.
   */
  const showRows = (ids: number[]) => {
    setFilters({ ...emptyFilters, ids })
    setPage(0)
    navigate(`/transactions${buildQuery({ ids })}`)
  }

  const applyFilters = (next: TxFilters) => {
    setFilters(next)
    setPage(0)
  }

  /**
   * Clears both the filters and the query string. The list reads its filters from the URL, so
   * leaving the old query string behind would make the next drill-down to the same target look
   * like no navigation at all and silently do nothing.
   */
  const clearFilters = () => {
    setFilters(emptyFilters)
    setPage(0)
    navigate('/transactions')
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
          title="Internal transfers"
          subtitle={`${suggestionsQuery.data?.length ?? 0} ${
            suggestionsQuery.data?.length === 1 ? 'pair' : 'pairs'
          } that look like moves between your own accounts but are not internal transfers yet.`}
          suggestions={suggestionsQuery.data ?? []}
          busy={applyAllMutation.isPending || applyOneMutation.isPending}
          rowKey={(suggestion) => `${suggestion.fromTransactionId}-${suggestion.toTransactionId}`}
          renderRow={(suggestion) => (
            <TransferSuggestionRow suggestion={suggestion} accounts={accountsById} />
          )}
          onApplyAll={() => applyAllMutation.mutate()}
          onApply={(suggestion) => applyOneMutation.mutate(suggestion)}
          onSelect={(suggestion) =>
            showRows([suggestion.fromTransactionId, suggestion.toTransactionId])
          }
        />
      ) : null}

      {(refundSuggestionsQuery.data?.length ?? 0) > 0 ? (
        <SuggestionsPanel
          title="Refunds"
          subtitle={`${refundSuggestionsQuery.data?.length ?? 0} ${
            refundSuggestionsQuery.data?.length === 1 ? 'purchase' : 'purchases'
          } that came back. Linking both legs takes them out of your spending and income.`}
          suggestions={refundSuggestionsQuery.data ?? []}
          busy={applyAllRefundsMutation.isPending || applyRefundMutation.isPending}
          rowKey={(suggestion) => `${suggestion.purchaseTransactionId}-${suggestion.refundTransactionIds.join('.')}`}
          renderRow={(suggestion) => (
            <RefundSuggestionRow suggestion={suggestion} accounts={accountsById} />
          )}
          onApplyAll={() => applyAllRefundsMutation.mutate()}
          onApply={(suggestion) => applyRefundMutation.mutate(suggestion)}
          onSelect={(suggestion) =>
            showRows([suggestion.purchaseTransactionId, ...suggestion.refundTransactionIds])
          }
        />
      ) : null}

      <TransactionFilters
        filters={filters}
        accounts={accounts}
        categories={categories}
        editMode={editMode}
        taggedIds={{
          refund: [...refundSuggestionIds],
          internal: [...suggestionIds],
        }}
        onChange={applyFilters}
        onClear={clearFilters}
        onEditModeChange={setEditMode}
      />

      {editMode && selected.size > 0 ? (
        <LinkActions
          selected={[...selected.values()]}
          busy={linkRefundMutation.isPending || linkTransferMutation.isPending}
          onLinkRefund={(purchaseId, refundIds) => linkRefundMutation.mutate({ purchaseId, refundIds })}
          onLinkTransfer={(fromId, toId) => linkTransferMutation.mutate({ fromId, toId })}
          onClear={() => setSelected(new Map())}
        />
      ) : null}

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
            containerRef={containerRef}
            rowsPerPage={rowsPerPage}
            rowHeight={rowHeight}
            editMode={editMode}
            selected={new Set(selected.keys())}
            allPageSelected={allPageSelected}
            somePageSelected={somePageSelected}
            onToggleSelect={toggleSelect}
            onSelectPage={selectPage}
            page={page}
            onPageChange={setPage}
            suggestionIds={suggestionIds}
            refundSuggestionIds={refundSuggestionIds}
            onCategoryChange={changeCategory}
            onUnlink={(id) => unlinkMutation.mutate(id)}
            onUnlinkRefund={(id) => unlinkRefundMutation.mutate(id)}
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
