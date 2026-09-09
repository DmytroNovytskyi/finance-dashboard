import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import type { AccountPresentation } from '../../api/queries'
import { formatDate, formatMoneyMagnitude } from '../../lib/format'
import type { TransferSuggestion } from '../../types'

interface SuggestionsPanelProps {
  suggestions: TransferSuggestion[]
  accounts: Map<number, AccountPresentation>
  busy: boolean
  onApplyAll: () => void
  onApply: (suggestion: TransferSuggestion) => void
}

/** Pending own-account transfer pairs: review and apply each or all at once. */
export function SuggestionsPanel({ suggestions, accounts, busy, onApplyAll, onApply }: SuggestionsPanelProps) {
  const accountName = (id: number) => accounts.get(id)?.name ?? `Account ${id}`
  return (
    <Paper variant="outlined" sx={{ borderRadius: 3, px: 2.5, py: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h6" component="h3">
            Internal transfers
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {suggestions.length} {suggestions.length === 1 ? 'pair' : 'pairs'} that look like moves between your own accounts but are not internal transfers yet.
          </Typography>
        </Box>
        <Button variant="outlined" onClick={onApplyAll} disabled={busy}>
          Apply all
        </Button>
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, maxHeight: 280, overflow: 'auto' }}>
        {suggestions.map((suggestion) => (
          <Box
            key={`${suggestion.fromTransactionId}-${suggestion.toTransactionId}`}
            sx={{ display: 'flex', alignItems: 'center', gap: 1.5, px: 1, py: 0.75, borderRadius: 1.5, '&:hover': { bgcolor: 'action.hover' } }}
          >
            <Box sx={{ minWidth: 0, flexGrow: 1 }}>
              <Typography variant="body2" noWrap>
                {formatMoneyMagnitude(suggestion.amount, suggestion.currency)} · {accountName(suggestion.fromAccountId)} → {accountName(suggestion.toAccountId)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {formatDate(suggestion.fromDate)}
                {suggestion.reason === 'AMOUNT' ? ' · equal-amount match' : ''}
              </Typography>
            </Box>
            <Button size="small" onClick={() => onApply(suggestion)} disabled={busy}>
              Apply
            </Button>
          </Box>
        ))}
      </Box>
    </Paper>
  )
}
