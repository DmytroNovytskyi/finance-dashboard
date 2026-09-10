import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TablePagination from '@mui/material/TablePagination'
import TableRow from '@mui/material/TableRow'
import TableSortLabel from '@mui/material/TableSortLabel'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import Undo from '@mui/icons-material/Undo'
import type { AccountPresentation, CategoryPresentation } from '../../api/queries'
import { amountColor, useScheme } from '../../theme'
import { formatDate, formatMoney } from '../../lib/format'
import type { PageResponse, Transaction } from '../../types'
import type { TransactionSortKey } from '../../api/endpoints'
import type { TransactionSort } from './model'

interface TransactionTableProps {
  data: PageResponse<Transaction> | undefined
  accounts: Map<number, AccountPresentation>
  categories: CategoryPresentation[]
  /** Current server-side sort of the list (columns sort on the backend). */
  sort: TransactionSort
  onSortChange: (sort: TransactionSort) => void
  /** Attach to the scrolling container; the page it lives on measures it to size each page. */
  containerRef: (node: HTMLElement | null) => void
  /** Rows one page holds, worked out from the space the container has. */
  rowsPerPage: number
  page: number
  onPageChange: (page: number) => void
  /** Ids of the legs of pending internal-transfer suggestions; those rows get an "Internal" tag. */
  suggestionIds: ReadonlySet<number>
  /** Ids of the legs of pending refund suggestions; those rows get a "Refund" tag. */
  refundSuggestionIds: ReadonlySet<number>
  onCategoryChange: (id: number, categoryId: number | null) => void
  onUnlink: (id: number) => void
  onUnlinkRefund: (id: number) => void
}

/** The bordered tag that marks a row as part of a detected pair. */
function PairTag({ label }: { label: string }) {
  return (
    <Box
      component="span"
      sx={{
        typography: 'caption',
        fontWeight: 600,
        color: 'text.secondary',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        px: 0.75,
        py: 0,
        mr: 1,
        whiteSpace: 'nowrap',
      }}
    >
      {label}
    </Box>
  )
}

/** Fixed label plus an undo button, for rows whose category the pairing has taken over. */
function PairedCategoryCell({
  label,
  action,
  onUnlink,
}: {
  label: string
  action: string
  onUnlink: () => void
}) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Tooltip title={action}>
        <IconButton size="small" aria-label={action} onClick={onUnlink}>
          <Undo fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  )
}

function AmountCell({ transaction }: { transaction: Transaction }) {
  const scheme = useScheme()
  const colors = amountColor[scheme]
  const excluded = transaction.nature === 'TRANSFER' || transaction.nature === 'REFUND'
  const color = excluded ? 'text.disabled' : transaction.amount >= 0 ? colors.income : colors.expense
  return (
    <Typography
      variant="body2"
      sx={{ fontVariantNumeric: 'tabular-nums', color, fontWeight: 500, whiteSpace: 'nowrap' }}
      align="right"
    >
      {formatMoney(transaction.amount, transaction.currency)}
    </Typography>
  )
}

/** Category cell: editable for income/expense rows, fixed + unlink for internal transfers. */
function CategoryCell({
  transaction,
  categories,
  onCategoryChange,
  onUnlink,
  onUnlinkRefund,
}: {
  transaction: Transaction
  categories: CategoryPresentation[]
  onCategoryChange: (id: number, categoryId: number | null) => void
  onUnlink: (id: number) => void
  onUnlinkRefund: (id: number) => void
}) {
  if (transaction.nature === 'TRANSFER') {
    return (
      <PairedCategoryCell
        label="Internal Transfer"
        action="Revert transfer to a normal expense/income"
        onUnlink={() => onUnlink(transaction.id)}
      />
    )
  }
  if (transaction.nature === 'REFUND') {
    return (
      <PairedCategoryCell
        label="Refund"
        action="Unlink the refund from its purchase"
        onUnlink={() => onUnlinkRefund(transaction.id)}
      />
    )
  }
  const editable = categories.filter((category) => !category.system)
  const value =
    transaction.categoryId !== null && editable.some((category) => category.id === transaction.categoryId)
      ? String(transaction.categoryId)
      : 'none'
  return (
    <Select
      size="small"
      value={value}
      onChange={(event) =>
        onCategoryChange(transaction.id, event.target.value === 'none' ? null : Number(event.target.value))
      }
      sx={{ minWidth: 140, maxWidth: 190, '.MuiSelect-select': { py: 0.5 } }}
      aria-label={`Category of transaction ${transaction.id}`}
    >
      <MenuItem value="none">
        <Typography variant="body2" color="text.disabled">
          Uncategorized
        </Typography>
      </MenuItem>
      {editable.map((category) => (
        <MenuItem key={category.id} value={String(category.id)}>
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
            <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: category.color, display: 'inline-block' }} />
            {category.name}
          </Box>
        </MenuItem>
      ))}
    </Select>
  )
}

/** Transaction list: the server returns the whole filtered set already sorted; paging is client-side. */
export function TransactionTable({
  data,
  accounts,
  categories,
  sort,
  onSortChange,
  containerRef,
  rowsPerPage,
  page,
  onPageChange,
  suggestionIds,
  refundSuggestionIds,
  onCategoryChange,
  onUnlink,
  onUnlinkRefund,
}: TransactionTableProps) {
  const allRows = data?.content ?? []

  const handleSort = (key: TransactionSortKey) => {
    onSortChange(
      sort.key === key
        ? { key, dir: sort.dir === 'asc' ? 'desc' : 'asc' }
        : { key, dir: 'asc' },
    )
  }
  const sortProps = (key: TransactionSortKey) => ({
    active: sort.key === key,
    direction: (sort.key === key ? sort.dir : 'asc') as 'asc' | 'desc',
    onClick: () => handleSort(key),
  })

  const total = data?.totalElements ?? 0
  const maxPage = Math.max(0, Math.ceil(total / rowsPerPage) - 1)
  const shownPage = Math.min(page, maxPage)

  return (
    <Paper
      variant="outlined"
      sx={{ borderRadius: 3, overflow: 'hidden', display: 'flex', flexDirection: 'column', flex: 1, minHeight: 140 }}
    >
      <TableContainer ref={containerRef} sx={{ flex: 1, minHeight: 0 }}>
        <Table size="small" stickyHeader sx={{ minWidth: 860 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 110 }}>
                <TableSortLabel {...sortProps('date')}>Date</TableSortLabel>
              </TableCell>
              <TableCell>Description</TableCell>
              <TableCell sx={{ width: 170 }}>
                <TableSortLabel {...sortProps('account')}>Account</TableSortLabel>
              </TableCell>
              <TableCell sx={{ width: 200 }}>
                <TableSortLabel {...sortProps('category')}>Category</TableSortLabel>
              </TableCell>
              <TableCell align="right" sx={{ width: 140 }}>
                <TableSortLabel {...sortProps('amount')}>Amount</TableSortLabel>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {allRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5}>
                  <Box sx={{ py: 6, textAlign: 'center' }}>
                    <Typography color="text.secondary">No transactions match these filters.</Typography>
                  </Box>
                </TableCell>
              </TableRow>
            ) : (
              allRows.map((transaction) => {
                const account = accounts.get(transaction.accountId)
                const isSuggested = transaction.nature !== 'TRANSFER' && suggestionIds.has(transaction.id)
                const isRefundSuggested =
                  transaction.nature !== 'REFUND' && refundSuggestionIds.has(transaction.id)
                return (
                  <TableRow key={transaction.id} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                      {formatDate(transaction.transactionDate)}
                    </TableCell>
                    <TableCell>
                      {isSuggested ? <PairTag label="Internal" /> : null}
                      {isRefundSuggested ? <PairTag label="Refund" /> : null}
                      <Typography variant="body2">{transaction.description || transaction.merchant || '—'}</Typography>
                      {transaction.merchant && transaction.merchant !== transaction.description ? (
                        <Typography variant="caption" color="text.secondary">
                          {transaction.merchant}
                        </Typography>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary">
                        {account?.name ?? `Account ${transaction.accountId}`}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <CategoryCell
                        transaction={transaction}
                        categories={categories}
                        onCategoryChange={onCategoryChange}
                        onUnlink={onUnlink}
                        onUnlinkRefund={onUnlinkRefund}
                      />
                    </TableCell>
                    <TableCell>
                      <AmountCell transaction={transaction} />
                    </TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {total > rowsPerPage ? (
        <TablePagination
          component="div"
          count={total}
          page={shownPage}
          rowsPerPage={rowsPerPage}
          rowsPerPageOptions={[]}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} of ${count}`}
          onPageChange={(_, nextPage) => onPageChange(nextPage)}
          sx={{ flexShrink: 0 }}
        />
      ) : null}
    </Paper>
  )
}
