import Typography from '@mui/material/Typography'
import type { AccountPresentation } from '../../api/queries'
import { formatDate, formatMoneyMagnitude } from '../../lib/format'
import type { RefundSuggestion, TransferSuggestion } from '../../types'

/** Text block of one suggested own-account transfer: both accounts, the sum, and the match basis. */
export function TransferSuggestionRow({
  suggestion,
  accounts,
}: {
  suggestion: TransferSuggestion
  accounts: Map<number, AccountPresentation>
}) {
  const accountName = (id: number) => accounts.get(id)?.name ?? `Account ${id}`
  return (
    <>
      <Typography variant="body2" noWrap>
        {formatMoneyMagnitude(suggestion.amount, suggestion.currency)} ·{' '}
        {accountName(suggestion.fromAccountId)} → {accountName(suggestion.toAccountId)}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {formatDate(suggestion.fromDate)}
        {suggestion.reason === 'AMOUNT' ? ' · equal-amount match' : ''}
      </Typography>
    </>
  )
}

/** Text block of one suggested refund: the purchase it reverses, when, and why it was matched. */
export function RefundSuggestionRow({
  suggestion,
  accounts,
}: {
  suggestion: RefundSuggestion
  accounts: Map<number, AccountPresentation>
}) {
  const accountName = accounts.get(suggestion.accountId)?.name ?? `Account ${suggestion.accountId}`
  return (
    <>
      <Typography variant="body2" noWrap>
        {formatMoneyMagnitude(suggestion.amount, suggestion.currency)}
        {suggestion.merchant ? ` · ${suggestion.merchant}` : ''}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {accountName} · bought {formatDate(suggestion.purchaseDate)} · refunded{' '}
        {formatDate(suggestion.refundDate)}
        {suggestion.reason === 'AMOUNT' ? ' · equal-amount match' : ''}
      </Typography>
    </>
  )
}
