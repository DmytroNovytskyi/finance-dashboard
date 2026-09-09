import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TablePagination from '@mui/material/TablePagination'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import type { AccountPresentation, CategoryPresentation } from '../../api/queries'
import { amountColor, useScheme } from '../../theme'
import { formatDate, formatMoney } from '../../lib/format'
import type { PageResponse, Transaction } from '../../types'

interface TransactionTableProps {
  data: PageResponse<Transaction> | undefined
  accounts: Map<number, AccountPresentation>
  categories: CategoryPresentation[]
  page: number
  size: number
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

function AmountCell({ transaction }: { transaction: Transaction }) {
  const scheme = useScheme()
  const colors = amountColor[scheme]
  const color = transaction.nature === 'TRANSFER' ? 'text.disabled' : transaction.amount >= 0 ? colors.income : colors.expense
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

/** Read-only paged table of transactions with per-account and per-category labels. */
export function TransactionTable({ data, accounts, categories, page, size, onPageChange, onSizeChange }: TransactionTableProps) {
  const total = data?.totalElements ?? 0
  const rows = data?.content ?? []
  const rowsPerPageOptions = [25, 50, 100]

  const categoryById = new Map(categories.map((category) => [category.id, category]))

  return (
    <Paper variant="outlined" sx={{ borderRadius: 3, overflow: 'hidden' }}>
      <TableContainer>
        <Table size="small" sx={{ minWidth: 760 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 110 }}>Date</TableCell>
              <TableCell>Description</TableCell>
              <TableCell sx={{ width: 170 }}>Account</TableCell>
              <TableCell sx={{ width: 180 }}>Category</TableCell>
              <TableCell align="right" sx={{ width: 140 }}>
                Amount
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5}>
                  <Box sx={{ py: 6, textAlign: 'center' }}>
                    <Typography color="text.secondary">No transactions match these filters.</Typography>
                  </Box>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((transaction) => {
                const account = accounts.get(transaction.accountId)
                const category = transaction.categoryId !== null ? categoryById.get(transaction.categoryId) : undefined
                return (
                  <TableRow key={transaction.id} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                      {formatDate(transaction.transactionDate)}
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                        <Typography variant="body2">{transaction.description || transaction.merchant || '—'}</Typography>
                        {transaction.nature === 'TRANSFER' ? (
                          <Chip label="Transfer" size="small" variant="outlined" color="info" />
                        ) : null}
                      </Box>
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
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        {category ? (
                          <>
                            <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: category.color, flexShrink: 0 }} />
                            <Typography variant="body2">{category.name}</Typography>
                          </>
                        ) : (
                          <Typography variant="body2" color="text.disabled">
                            Uncategorized
                          </Typography>
                        )}
                      </Box>
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
      <TablePagination
        component="div"
        count={total}
        page={page}
        rowsPerPage={size}
        rowsPerPageOptions={rowsPerPageOptions}
        onPageChange={(_, nextPage) => onPageChange(nextPage)}
        onRowsPerPageChange={(event) => onSizeChange(Number(event.target.value))}
      />
    </Paper>
  )
}
