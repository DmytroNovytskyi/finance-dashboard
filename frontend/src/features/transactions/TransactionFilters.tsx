import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Edit from '@mui/icons-material/Edit'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import type { AccountPresentation, CategoryPresentation } from '../../api/queries'
import { DateRangeControl } from '../../components/DateRangeControl'
import type { TransactionNature } from '../../types'
import { hasFilters, tagWordOf, type TxFilters } from './model'

interface TransactionFiltersProps {
  filters: TxFilters
  accounts: AccountPresentation[]
  categories: CategoryPresentation[]
  /** Whether the page is in edit mode; the toggle in this bar drives it. */
  editMode: boolean
  /** Ids of the rows carrying each pair tag, so a tag word in the search box can select them. */
  taggedIds: { refund: number[]; internal: number[] }
  onChange: (filters: TxFilters) => void
  onClear: () => void
  onEditModeChange: (editMode: boolean) => void
}

/** Filter bar for the transactions list: search, account, category, nature, date range, edit mode. */
export function TransactionFilters({
  filters,
  accounts,
  categories,
  editMode,
  taggedIds,
  onChange,
  onClear,
  onEditModeChange,
}: TransactionFiltersProps) {
  const patch = (changes: Partial<TxFilters>) => onChange({ ...filters, ...changes })

  const setSearch = (value: string) => {
    const tokens = value.trim().split(/\s+/).filter(Boolean)
    if (tokens.length > 0 && tokens.every((token) => /^\d+$/.test(token))) {
      patch({ ids: tokens.map(Number), q: '' })
      return
    }
    const tag = tagWordOf(value)
    if (tag) {
      patch({ ids: taggedIds[tag], q: value.trim() })
      return
    }
    patch({ ids: undefined, q: value })
  }

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
        placeholder="Search description, merchant, or transaction ids"
        value={filters.q || (filters.ids && filters.ids.length > 0 ? filters.ids.join(' ') : '')}
        onChange={(event) => setSearch(event.target.value)}
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
      </TextField>
      <DateRangeControl
        mode={filters.dateMode}
        modes={['allTime', 'month', 'dates']}
        range={{ from: filters.from ?? null, to: filters.to ?? null }}
        onChange={(dateMode, range) =>
          patch({ dateMode, from: range.from ?? undefined, to: range.to ?? undefined })
        }
      />
      {filters.merchant !== undefined || filters.withoutMerchant ? (
        <Chip
          variant="outlined"
          label={filters.withoutMerchant ? 'No merchant' : `Merchant: ${filters.merchant}`}
          onDelete={() => patch({ merchant: undefined, withoutMerchant: undefined })}
          sx={{
            height: 40,
            borderRadius: 2,
            pl: 0.5,
            '.MuiChip-label': { px: 1.5, fontSize: '0.9375rem' },
            '.MuiChip-deleteIcon': { fontSize: 20, mr: 1 },
          }}
        />
      ) : null}
      <Button size="small" onClick={onClear} disabled={!hasFilters(filters)}>
        Clear
      </Button>
      <ToggleButton
        size="small"
        value="edit"
        selected={editMode}
        onChange={() => onEditModeChange(!editMode)}
        sx={{ ml: 'auto' }}
        aria-label="Edit transactions"
      >
        <Edit fontSize="small" sx={{ mr: 0.5 }} />
        Edit
      </ToggleButton>
    </Paper>
  )
}
