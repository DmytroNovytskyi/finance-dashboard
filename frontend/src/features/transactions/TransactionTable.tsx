import { useEffect } from 'react'
import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import IconButton from '@mui/material/IconButton'
import LinearProgress from '@mui/material/LinearProgress'
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

/**
 * Unstretched height of one row, taken from the tallest a row gets: three lines when it carries a
 * pair tag above a two-line description, and two otherwise. Rows share out whatever height the
 * table has left, and this is the floor that share is worked out from, so the page always fills
 * the table exactly and never scrolls. Taking the shorter height instead would let a page holding
 * a tagged row overflow, because a stretched row cannot be shorter than its own content.
 */
export const TABLE_ROW_HEIGHT = 75

/**
 * Width of the selection column. Set as an inline style because it has to beat the table cell's own
 * class: the cell keeps a 16px inset and holds a 20px checkbox, and left to itself the column comes
 * out narrower than that, so the checkbox runs into the Date heading beside it.
 */
const CHECKBOX_CELL_SIZE = { width: 48, minWidth: 48 }

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
  /** Height each row takes so the page fills the table, or null before it has been measured. */
  rowHeight: number | null
  /** Whether a page of rows is on its way, drawn as a strip inside this card rather than above it. */
  busy: boolean
  /** Whether the page is in edit mode: selection, linking and category editing are offered. */
  editMode: boolean
  /** Ids of the selected rows; only meaningful in edit mode. */
  selected: ReadonlySet<number>
  /** Whether every row on the current page is selected, for the header checkbox. */
  allPageSelected: boolean
  /** Whether some but not all rows on the page are selected. */
  somePageSelected: boolean
  onToggleSelect: (transaction: Transaction) => void
  onSelectPage: (checked: boolean) => void
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

/**
 * The chip's own left padding plus its border. Pulling the chip back by exactly this much puts its
 * label on the same left edge as the description and counterparty beneath it, which the chip's box
 * would otherwise sit one inset to the right of.
 */
const TAG_INSET_PX = 7

/**
 * The bordered tag that marks a row as part of a detected pair. It is inline-block, and the
 * description below it is a block, so the tag takes a line of its own rather than indenting the
 * description away from the counterparty under it.
 */
function PairTag({ label }: { label: string }) {
  return (
    <Box
      component="span"
      sx={{
        typography: 'body2',
        fontWeight: 600,
        color: 'text.secondary',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 1,
        px: 0.75,
        py: 0,
        lineHeight: 1.2,
        display: 'inline-block',
        ml: `-${TAG_INSET_PX}px`,
        mb: 0.25,
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
  editable,
}: {
  label: string
  action: string
  onUnlink: () => void
  /** Whether the unlink button is offered; it is an editing action, so read mode shows the label alone. */
  editable: boolean
}) {
  if (!editable) {
    return (
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    )
  }
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
  const color = transaction.amount >= 0 ? colors.income : colors.expense
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
  editable,
  onCategoryChange,
  onUnlink,
  onUnlinkRefund,
}: {
  transaction: Transaction
  categories: CategoryPresentation[]
  /** Whether the category may be changed; read mode shows the name alone. */
  editable: boolean
  onCategoryChange: (id: number, categoryId: number | null) => void
  onUnlink: (id: number) => void
  onUnlinkRefund: (id: number) => void
}) {
  const nameOf = (id: number) => categories.find((category) => category.id === id)?.name ?? null

  if (!editable) {
    const paired =
      transaction.nature === 'TRANSFER'
        ? 'Internal Transfer'
        : transaction.nature === 'REFUND'
          ? 'Refund'
          : null
    const label = paired ?? nameOf(transaction.categoryId ?? -1)
    return (
      <Typography variant="body2" color={label ? 'text.secondary' : 'text.disabled'} noWrap>
        {label ?? 'Uncategorized'}
      </Typography>
    )
  }

  if (transaction.nature === 'TRANSFER') {
    return (
      <PairedCategoryCell
        label="Internal Transfer"
        action="Revert transfer to a normal expense/income"
        onUnlink={() => onUnlink(transaction.id)}
        editable={editable}
      />
    )
  }
  if (transaction.nature === 'REFUND') {
    return (
      <PairedCategoryCell
        label="Refund"
        action="Unlink the refund from its purchase"
        onUnlink={() => onUnlinkRefund(transaction.id)}
        editable={editable}
      />
    )
  }
  const assignable = categories.filter((category) => !category.system)
  const value =
    transaction.categoryId !== null && assignable.some((category) => category.id === transaction.categoryId)
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
      {assignable.map((category) => (
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
  rowHeight,
  busy,
  editMode,
  selected,
  allPageSelected,
  somePageSelected,
  onToggleSelect,
  onSelectPage,
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

  useEffect(() => {
    if (shownPage !== page) onPageChange(shownPage)
  }, [shownPage, page, onPageChange])

  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 3,
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        minHeight: 140,
        position: 'relative',
      }}
    >
      <Box sx={{ position: 'absolute', top: 0, left: 0, right: 0, height: 4, zIndex: 2 }}>
        {busy ? <LinearProgress sx={{ borderRadius: 1 }} /> : null}
      </Box>
      <TableContainer ref={containerRef} sx={{ flex: 1, minHeight: 0, overscrollBehaviorY: 'contain' }}>
        <Table size="small" stickyHeader sx={{ minWidth: 860, tableLayout: 'fixed' }}>
          <TableHead>
            <TableRow>
              {editMode ? (
                <TableCell padding="checkbox" style={CHECKBOX_CELL_SIZE}>
                  <Checkbox
                    size="small"
                    indeterminate={somePageSelected && !allPageSelected}
                    checked={allPageSelected}
                    onChange={(event) => onSelectPage(event.target.checked)}
                    aria-label="Select all on this page"
                  />
                </TableCell>
              ) : null}
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
                <TableCell colSpan={editMode ? 6 : 5}>
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
                  <TableRow key={transaction.id} hover sx={{ height: rowHeight ?? TABLE_ROW_HEIGHT }}>
                    {editMode ? (
                      <TableCell padding="checkbox" style={CHECKBOX_CELL_SIZE}>
                        <Checkbox
                          size="small"
                          checked={selected.has(transaction.id)}
                          onChange={() => onToggleSelect(transaction)}
                          aria-label={`Select transaction ${transaction.id}`}
                        />
                      </TableCell>
                    ) : null}
                    <TableCell sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                      {formatDate(transaction.transactionDate)}
                    </TableCell>
                    <TableCell>
                      {isSuggested ? <PairTag label="Internal" /> : null}
                      {isRefundSuggested ? <PairTag label="Refund" /> : null}
                      <Typography variant="body2" noWrap>
                        {transaction.description || transaction.merchant || '—'}
                      </Typography>
                      {transaction.merchant && transaction.merchant !== transaction.description ? (
                        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
                          {transaction.merchant}
                        </Typography>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {account?.name ?? `Account ${transaction.accountId}`}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <CategoryCell
                        transaction={transaction}
                        categories={categories}
                        editable={editMode}
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
