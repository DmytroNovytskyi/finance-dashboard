import Button from '@mui/material/Button'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { AccountPresentation, CategoryPresentation } from '../../api/queries'
import type { TransactionNature } from '../../types'
import { hasFilters, type TxFilters } from './model'

interface TransactionFiltersProps {
  filters: TxFilters
  accounts: AccountPresentation[]
  categories: CategoryPresentation[]
  onChange: (filters: TxFilters) => void
  onClear: () => void
}

/** Filter bar for the transactions list: search, account, category, nature, date range. */
export function TransactionFilters({ filters, accounts, categories, onChange, onClear }: TransactionFiltersProps) {
  const patch = (changes: Partial<TxFilters>) => onChange({ ...filters, ...changes })

  const setCategory = (value: string) => {
    if (value === '') patch({ categoryId: undefined, uncategorized: false })
    else if (value === 'uncategorized') patch({ categoryId: undefined, uncategorized: true })
    else patch({ categoryId: Number(value), uncategorized: false })
  }

  const categoryValue = filters.uncategorized ? 'uncategorized' : (filters.categoryId ? String(filters.categoryId) : '')

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5, display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
      <TextField
        size="small"
        placeholder="Search description or merchant"
        value={filters.q}
        onChange={(event) => patch({ q: event.target.value })}
        sx={{ flexGrow: 1, minWidth: 220 }}
      />
      <TextField
        select
        size="small"
        label="Account"
        value={filters.accountId ? String(filters.accountId) : ''}
        onChange={(event) => patch({ accountId: event.target.value ? Number(event.target.value) : undefined })}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="">All accounts</MenuItem>
        {accounts.map((account) => (
          <MenuItem key={account.id} value={String(account.id)}>
            {account.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label="Category"
        value={categoryValue}
        onChange={(event) => setCategory(event.target.value)}
        sx={{ minWidth: 160 }}
      >
        <MenuItem value="">All categories</MenuItem>
        <MenuItem value="uncategorized">Uncategorized</MenuItem>
        {categories.map((category) => (
          <MenuItem key={category.id} value={String(category.id)}>
            {category.name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label="Nature"
        value={filters.nature ?? ''}
        onChange={(event) => patch({ nature: (event.target.value || undefined) as TransactionNature | undefined })}
        sx={{ minWidth: 140 }}
      >
        <MenuItem value="">All</MenuItem>
        <MenuItem value="INCOME">Income</MenuItem>
        <MenuItem value="EXPENSE">Expense</MenuItem>
        <MenuItem value="TRANSFER">Internal Transfer</MenuItem>
        <MenuItem value="REFUND">Refund</MenuItem>
      </TextField>
      <TextField
        type="date"
        size="small"
        label="From"
        value={filters.from ?? ''}
        onChange={(event) => patch({ from: event.target.value || undefined })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      <TextField
        type="date"
        size="small"
        label="To"
        value={filters.to ?? ''}
        onChange={(event) => patch({ to: event.target.value || undefined })}
        slotProps={{ inputLabel: { shrink: true } }}
      />
      {filters.ids && filters.ids.length > 0 ? (
        <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
          Showing the {filters.ids.length} transactions of one suggestion
        </Typography>
      ) : null}
      {hasFilters(filters) ? (
        <Button size="small" onClick={onClear}>
          Clear
        </Button>
      ) : null}
    </Paper>
  )
}
