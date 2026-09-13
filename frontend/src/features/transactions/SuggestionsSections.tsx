import { useMutation, useQueryClient } from '@tanstack/react-query'
import Box from '@mui/material/Box'
import useMediaQuery from '@mui/material/useMediaQuery'
import { refundsApi, transfersApi } from '../../api/endpoints'
import { useAccountsById, useRefundSuggestions, useTransferSuggestions } from '../../api/queries'
import type { RefundSuggestion, TransferSuggestion } from '../../types'
import { invalidatePairs } from './invalidatePairs'
import { RefundSuggestionRow, TransferSuggestionRow } from './SuggestionRows'
import { SuggestionsPanel } from './SuggestionsPanel'

interface SuggestionsSectionsProps {
  /** Shows the transactions one suggestion refers to: the two legs, or the purchase and its credits. */
  onSelect: (ids: number[]) => void
}

/**
 * How many rows the panels may show, by window height. Three is the cap, but a page that carries
 * three working lists below them has to give those lists room, so the lists give up their spare rows
 * first — they drop to a single row well before the panels lose theirs — and only the third row is
 * traded away. Measured against the layout probe, not estimated. Below ~858px even one row costs
 * more than the page's fixed chrome can spare, so the content area scrolls by the difference: 9px
 * at 840, 29 at 800, 79 at 700. What is left there is chrome, not rows — 60px of it is the
 * three-line subtitle in the merchant-defaults panel — so it cannot be reclaimed by row counts.
 */
const ROOMY_WINDOW = '(min-height: 1000px)'

const MAX_ROWS = 3
const MAX_ROWS_SHORT = 1

/**
 * The two panels share a row, and either one alone takes the whole of it. Side by side they cost
 * half the height that stacking them did, which is what lets a page with three working lists below
 * them stay inside the viewport.
 */
const rowSx = {
  display: 'flex',
  gap: 2,
  alignItems: 'flex-start',
  '& > *': { flex: '1 1 0', minWidth: 0 },
}

const plural = (count: number, one: string, many: string) => (count === 1 ? one : many)

/**
 * Both pending-pair panels — internal transfers and refunds — together with the actions that apply
 * them. The transactions list and the categorize queue review the same suggestions, so the panels
 * and their mutations live here rather than being wired up twice; a page that also needs the ids,
 * for tagging the rows, reads them from the same queries, which are cached.
 */
export function SuggestionsSections({ onSelect }: SuggestionsSectionsProps) {
  const queryClient = useQueryClient()
  const accounts = useAccountsById()
  const transfers = useTransferSuggestions()
  const refunds = useRefundSuggestions()
  const roomy = useMediaQuery(ROOMY_WINDOW)
  const maxRows = roomy ? MAX_ROWS : MAX_ROWS_SHORT

  const invalidate = () => invalidatePairs(queryClient)

  const applyAllTransfers = useMutation({ mutationFn: () => transfersApi.applyAll(), onSuccess: invalidate })
  const applyTransfer = useMutation({
    mutationFn: (suggestion: TransferSuggestion) =>
      transfersApi.pair(suggestion.fromTransactionId, suggestion.toTransactionId),
    onSuccess: invalidate,
  })
  const applyAllRefunds = useMutation({ mutationFn: () => refundsApi.applyAll(), onSuccess: invalidate })
  const applyRefund = useMutation({
    mutationFn: (suggestion: RefundSuggestion) =>
      refundsApi.pair([suggestion.purchaseTransactionId, ...suggestion.refundTransactionIds]),
    onSuccess: invalidate,
  })

  const transferSuggestions = transfers.data ?? []
  const refundSuggestions = refunds.data ?? []

  if (transferSuggestions.length === 0 && refundSuggestions.length === 0) {
    return null
  }

  return (
    <Box sx={rowSx}>
      {transferSuggestions.length > 0 ? (
        <SuggestionsPanel
          title={`Internal transfers (${transferSuggestions.length})`}
          hint={`${transferSuggestions.length} ${plural(transferSuggestions.length, 'pair', 'pairs')} that look like moves between your own accounts but are not internal transfers yet.`}
          maxRows={maxRows}
          suggestions={transferSuggestions}
          busy={applyAllTransfers.isPending || applyTransfer.isPending}
          rowKey={(suggestion) => `${suggestion.fromTransactionId}-${suggestion.toTransactionId}`}
          renderRow={(suggestion) => <TransferSuggestionRow suggestion={suggestion} accounts={accounts} />}
          onApplyAll={() => applyAllTransfers.mutate()}
          onApply={(suggestion) => applyTransfer.mutate(suggestion)}
          onSelect={(suggestion) => onSelect([suggestion.fromTransactionId, suggestion.toTransactionId])}
        />
      ) : null}

      {refundSuggestions.length > 0 ? (
        <SuggestionsPanel
          title={`Refunds (${refundSuggestions.length})`}
          hint={`${refundSuggestions.length} ${plural(refundSuggestions.length, 'purchase', 'purchases')} that came back. Linking both legs takes them out of your spending and income.`}
          maxRows={maxRows}
          suggestions={refundSuggestions}
          busy={applyAllRefunds.isPending || applyRefund.isPending}
          rowKey={(suggestion) => `${suggestion.purchaseTransactionId}-${suggestion.refundTransactionIds.join('.')}`}
          renderRow={(suggestion) => <RefundSuggestionRow suggestion={suggestion} accounts={accounts} />}
          onApplyAll={() => applyAllRefunds.mutate()}
          onApply={(suggestion) => applyRefund.mutate(suggestion)}
          onSelect={(suggestion) =>
            onSelect([suggestion.purchaseTransactionId, ...suggestion.refundTransactionIds])
          }
        />
      ) : null}
    </Box>
  )
}
