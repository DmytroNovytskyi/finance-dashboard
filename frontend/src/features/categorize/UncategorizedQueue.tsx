import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import CircularProgress from '@mui/material/CircularProgress'
import FormControlLabel from '@mui/material/FormControlLabel'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import ToggleButton from '@mui/material/ToggleButton'
import TablePagination from '@mui/material/TablePagination'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import type { CategoryPresentation } from '../../api/queries'
import { useFittingRows } from '../../hooks/useFittingRows'
import { amountColor, useScheme } from '../../theme'
import { formatDate, formatMoney } from '../../lib/format'
import type { Transaction } from '../../types'
import {
  useCategorizeBulk,
  useCategorizeOne,
  useCreateMerchantRule,
  useMerchantRules,
  useUncategorizedQueue,
} from './hooks'

interface UncategorizedQueueProps {
  categories: CategoryPresentation[]
}

const QUEUE_SIZE = 200

function normalizeMerchant(value: string): string {
  return value.trim().toUpperCase().replace(/\s+/g, ' ')
}

function RowAmount({ transaction }: { transaction: Transaction }) {
  const scheme = useScheme()
  const colors = amountColor[scheme]
  const color = transaction.amount >= 0 ? colors.income : colors.expense
  return (
    <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', color, fontWeight: 500, whiteSpace: 'nowrap' }}>
      {formatMoney(transaction.amount, transaction.currency)}
    </Typography>
  )
}

/** Uncategorized rows of one nature, selectable for bulk assignment to a category. */
export function UncategorizedQueue({ categories }: UncategorizedQueueProps) {
  const [nature, setNature] = useState<'EXPENSE' | 'INCOME'>('EXPENSE')
  const [selected, setSelected] = useState<ReadonlySet<number>>(new Set())
  const [remember, setRemember] = useState(false)
  const queue = useUncategorizedQueue(nature)
  const categorizeOne = useCategorizeOne()
  const categorizeBulk = useCategorizeBulk()
  const rules = useMerchantRules()
  const createRule = useCreateMerchantRule()

  const rows = queue.data?.content ?? []
  const total = queue.data?.totalElements ?? 0
  const [page, setPage] = useState(0)
  const { containerRef, rows: perPage } = useFittingRows(rows.length, { rowSelector: '[data-row]' })
  const maxPage = Math.max(0, Math.ceil(rows.length / perPage) - 1)
  const shownPage = Math.min(page, maxPage)
  const pageRows = rows.slice(shownPage * perPage, shownPage * perPage + perPage)
  const allPageSelected = pageRows.length > 0 && pageRows.every((row) => selected.has(row.id))
  const somePageSelected = pageRows.some((row) => selected.has(row.id))

  const single = selected.size === 1 ? rows.find((row) => selected.has(row.id)) : undefined
  const merchant = single?.merchant ?? null
  const hasRule = merchant !== null && (rules.data ?? []).some((rule) => rule.merchant === normalizeMerchant(merchant))

  const toggle = (id: number) => {
    const next = new Set(selected)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setSelected(next)
  }

  const assignOne = (id: number, categoryId: number) => categorizeOne.mutate({ id, categoryId })
  const assignMany = (categoryId: number) => {
    if (selected.size === 0) return
    if (remember && merchant) {
      createRule.mutate({ merchant, categoryId })
    }
    categorizeBulk.mutate({ ids: [...selected], categoryId })
    setSelected(new Set())
    setRemember(false)
  }

  const selectPage = (checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current)
      pageRows.forEach((row) => (checked ? next.add(row.id) : next.delete(row.id)))
      return next
    })
  }

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2.5,
        borderRadius: 3,
        display: 'flex',
        flexDirection: 'column',
        gap: 1.5,
        height: '100%',
        minWidth: 0,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h6" component="h3">
            Uncategorized
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {queue.isLoading ? 'Loading…' : `${total} rows`}
          </Typography>
        </Box>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={nature}
          onChange={(_, value) => {
            if (value) {
              setNature(value)
              setSelected(new Set())
              setRemember(false)
              setPage(0)
            }
          }}
        >
          <ToggleButton value="EXPENSE">Expenses</ToggleButton>
          <ToggleButton value="INCOME">Income</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      <Box
        sx={{
          minHeight: 44,
          px: 2,
          borderRadius: 2,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          flexWrap: 'wrap',
          bgcolor: selected.size > 0 ? 'action.selected' : 'transparent',
        }}
      >
        {selected.size > 0 ? (
          <>
            <Typography variant="body2" sx={{ mr: 'auto' }}>
              {selected.size} selected
            </Typography>
            {single && merchant ? (
              <FormControlLabel
                control={
                  <Checkbox size="small" checked={remember} disabled={hasRule} onChange={(event) => setRemember(event.target.checked)} />
                }
                label={
                  <Typography variant="body2" color={hasRule ? 'text.disabled' : 'text.secondary'}>
                    {hasRule ? 'Already has a default' : `Remember ${merchant.slice(0, 40)}`}
                  </Typography>
                }
                sx={{ m: 0 }}
              />
            ) : null}
            <Select
              size="small"
              displayEmpty
              value=""
              onChange={(event) => assignMany(Number(event.target.value))}
              renderValue={() => 'Assign category…'}
              sx={{ minWidth: 200 }}
            >
              {categories.map((category) => (
                <MenuItem key={category.id} value={String(category.id)}>
                  {category.name}
                </MenuItem>
              ))}
            </Select>
            <Button size="small" onClick={() => setSelected(new Set())}>
              Clear
            </Button>
          </>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Select rows to assign a category in bulk.
          </Typography>
        )}
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.secondary' }}>
        <Checkbox
          size="small"
          indeterminate={somePageSelected && !allPageSelected}
          checked={allPageSelected}
          onChange={(event) => selectPage(event.target.checked)}
        />
        <Typography variant="caption">Select page</Typography>
      </Box>

      <Box
        ref={containerRef}
        sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, flex: 1, minWidth: 0, minHeight: 48, overflowY: 'auto' }}
      >
        {queue.isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress />
          </Box>
        ) : rows.length === 0 ? (
          <Typography color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
            Nothing left to categorize. Nice work.
          </Typography>
        ) : (
          pageRows.map((transaction) => (
            <Box
              key={transaction.id}
              data-row="queue"
              sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minHeight: 48, py: 0.75, px: 1, borderRadius: 1.5, '&:hover': { bgcolor: 'action.hover' } }}
            >
              <Checkbox size="small" checked={selected.has(transaction.id)} onChange={() => toggle(transaction.id)} aria-label={`Select transaction ${transaction.id}`} />
              <Box sx={{ minWidth: 0, flexGrow: 1 }}>
                <Typography variant="body2" noWrap>
                  {transaction.description || transaction.merchant || '—'}
                </Typography>
                {transaction.merchant && transaction.merchant !== transaction.description ? (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }} noWrap>
                    {transaction.merchant}
                  </Typography>
                ) : null}
              </Box>
              <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0, fontVariantNumeric: 'tabular-nums' }}>
                {formatDate(transaction.transactionDate)}
              </Typography>
              <RowAmount transaction={transaction} />
              <Select
                size="small"
                displayEmpty
                value=""
                onChange={(event) => assignOne(transaction.id, Number(event.target.value))}
                renderValue={() => '…'}
                sx={{ minWidth: 44, '.MuiSelect-select': { py: 0.75 } }}
                inputProps={{ 'aria-label': 'Assign category' }}
              >
                {categories.map((category) => (
                  <MenuItem key={category.id} value={String(category.id)}>
                    <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
                      <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: category.color, display: 'inline-block' }} />
                      {category.name}
                    </Box>
                  </MenuItem>
                ))}
              </Select>
            </Box>
          ))
        )}
      </Box>

      {rows.length > perPage ? (
        <TablePagination
          component="div"
          count={rows.length}
          page={shownPage}
          onPageChange={(_event, nextPage) => setPage(nextPage)}
          rowsPerPage={perPage}
          rowsPerPageOptions={[]}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} of ${count}`}
          sx={{ flexShrink: 0, '.MuiTablePagination-toolbar': { minHeight: 40 } }}
        />
      ) : null}

      {!queue.isLoading && total > QUEUE_SIZE ? (
        <Typography variant="caption" color="text.disabled">
          Showing the first {QUEUE_SIZE} of {total}; use the Transactions page to filter the rest.
        </Typography>
      ) : null}
    </Paper>
  )
}
