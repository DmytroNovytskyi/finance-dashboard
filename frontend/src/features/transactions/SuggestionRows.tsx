import Typography from '@mui/material/Typography'
import type { AccountPresentation } from '../../api/queries'
import { formatDate, formatMoneyMagnitude } from '../../lib/format'
import type { RefundSuggestion, TransferSuggestion } from '../../types'

/**
 * One line of suggestion text, cut off with an ellipsis when the panel is too narrow for it and
 * readable in full on hover. Everything a row says goes through here so both kinds of suggestion
 * take the same single line and the same row height.
 */
function SuggestionLine({ text }: { text: string }) {
  return (
    <Typography variant="body2" noWrap title={text}>
      {text}
    </Typography>
  )
}

/** Text of one suggested own-account transfer: both accounts, the sum, the date, and the match basis. */
export function TransferSuggestionRow({
  suggestion,
  accounts,
}: {
  suggestion: TransferSuggestion
  accounts: Map<number, AccountPresentation>
}) {
  const accountName = (id: number) => accounts.get(id)?.name ?? `Account ${id}`
  const parts = [
    formatMoneyMagnitude(suggestion.amount, suggestion.currency),
    `${accountName(suggestion.fromAccountId)} → ${accountName(suggestion.toAccountId)}`,
    formatDate(suggestion.fromDate),
    suggestion.reason === 'AMOUNT' ? 'equal-amount match' : '',
  ]
  return <SuggestionLine text={parts.filter(Boolean).join(' · ')} />
}

/**
 * Text of one suggested refund: the purchase it reverses, when, how many credits gave it back, and
 * why it was matched.
 */
export function RefundSuggestionRow({
  suggestion,
  accounts,
}: {
  suggestion: RefundSuggestion
  accounts: Map<number, AccountPresentation>
}) {
  const accountName = accounts.get(suggestion.accountId)?.name ?? `Account ${suggestion.accountId}`
  const credits = suggestion.refundTransactionIds.length
  const parts = [
    formatMoneyMagnitude(suggestion.amount, suggestion.currency),
    suggestion.merchant,
    credits > 1 ? `${credits} credits` : '',
    accountName,
    `bought ${formatDate(suggestion.purchaseDate)}`,
    `refunded ${formatDate(suggestion.refundDate)}`,
    suggestion.reason === 'AMOUNT' ? 'equal-amount match' : '',
  ]
  return <SuggestionLine text={parts.filter(Boolean).join(' · ')} />
}
