import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { formatMoneyMagnitude } from '../../lib/format'
import type { Transaction } from '../../types'

interface LinkActionsProps {
  selected: Transaction[]
  busy: boolean
  onLinkRefund: (purchaseId: number, refundIds: number[]) => void
  onLinkTransfer: (fromId: number, toId: number) => void
  onClear: () => void
}

/** The two pairing buttons alone, for a bar that already has a selection count and a clear. */
interface LinkButtonsProps {
  selected: Transaction[]
  busy: boolean
  onLinkRefund: (purchaseId: number, refundIds: number[]) => void
  onLinkTransfer: (fromId: number, toId: number) => void
}

/** What a selection has to look like to become a refund, or the reason it cannot. */
interface RefundShape {
  purchase: Transaction
  credits: Transaction[]
}

/** What a selection has to look like to become an internal transfer, or the reason it cannot. */
interface TransferShape {
  from: Transaction
  to: Transaction
}

/**
 * An amount in minor units. Sums of decimals drift in binary floating point — 14.45 + 23.27 +
 * 23.74 comes to 61.46000000000001 — so comparing totals as they are would reject a refund group
 * that the backend accepts, which is exactly the one-purchase-reversed-by-several-credits case.
 */
function toMinor(value: number): number {
  return Math.round(value * 100)
}

/**
 * Works out whether a selection can be linked as a refund, treating the single expense as the
 * purchase and the incomes as the credits that reversed it. Returns the reason when it cannot, so
 * the button can say why rather than only refusing.
 */
function refundShape(selected: Transaction[]): RefundShape | string {
  const expenses = selected.filter((transaction) => transaction.amount < 0)
  const credits = selected.filter((transaction) => transaction.amount > 0)
  if (expenses.length === 0) return 'select the purchase too: the expense these credits reverse'
  if (expenses.length > 1) {
    return `${expenses.length} expenses selected; a refund reverses one purchase, so keep one and add as many credits as you like`
  }
  if (credits.length === 0) return 'add at least one income: the credit that came back'
  const [purchase] = expenses
  if (selected.some((transaction) => transaction.accountId !== purchase.accountId)) {
    return 'a refund must stay within one account'
  }
  if (selected.some((transaction) => transaction.currency !== purchase.currency)) {
    return 'a refund must stay within one currency'
  }
  const refundedMinor = credits.reduce((total, credit) => total + toMinor(credit.amount), 0)
  if (Math.abs(toMinor(purchase.amount)) !== refundedMinor) {
    return `the credits add up to ${formatMoneyMagnitude(refundedMinor / 100, purchase.currency)}, not ${formatMoneyMagnitude(purchase.amount, purchase.currency)}`
  }
  return { purchase, credits }
}

/**
 * Works out whether a selection can be linked as an internal transfer: one expense and one income,
 * necessarily in different accounts, since a transfer that stays put is not a transfer.
 */
function transferShape(selected: Transaction[]): TransferShape | string {
  const expenses = selected.filter((transaction) => transaction.amount < 0)
  const incomes = selected.filter((transaction) => transaction.amount > 0)
  if (expenses.length !== 1 || incomes.length !== 1) {
    return `a transfer is two rows, one out and one in; you selected ${expenses.length} out and ${incomes.length} in`
  }
  const [from] = expenses
  const [to] = incomes
  if (from.accountId === to.accountId) return 'the two legs must be in different accounts'
  if (from.currency !== to.currency) return 'the two legs must be in one currency'
  if (Math.abs(toMinor(from.amount)) !== toMinor(to.amount)) return 'the two legs must cancel out'
  return { from, to }
}

/**
 * The pairing buttons, disabled by the shape of the selection and explaining on hover why an
 * action is unavailable. Shared by every bar that offers linking, so a selection is judged in one
 * place however it was made.
 */
export function LinkButtons({ selected, busy, onLinkRefund, onLinkTransfer }: LinkButtonsProps) {
  const refund = refundShape(selected)
  const transfer = transferShape(selected)
  const refundReady = typeof refund !== 'string'
  const transferReady = typeof transfer !== 'string'

  return (
    <>
      <Tooltip title={refundReady ? 'Link the expense as the purchase and the incomes as its credits' : refund}>
        <span>
          <Button
            size="small"
            variant="contained"
            disabled={!refundReady || busy}
            onClick={() => {
              if (refundReady) {
                onLinkRefund(
                  refund.purchase.id,
                  refund.credits.map((credit) => credit.id),
                )
              }
            }}
          >
            Link as refund
          </Button>
        </span>
      </Tooltip>

      <Tooltip title={transferReady ? 'Link the two legs as one internal transfer' : transfer}>
        <span>
          <Button
            size="small"
            variant="contained"
            disabled={!transferReady || busy}
            onClick={() => {
              if (transferReady) onLinkTransfer(transfer.from.id, transfer.to.id)
            }}
          >
            Link as internal transfer
          </Button>
        </span>
      </Tooltip>
    </>
  )
}

/** Bar shown over the transactions table while rows are selected in edit mode. */
export function LinkActions({ selected, busy, onLinkRefund, onLinkTransfer, onClear }: LinkActionsProps) {
  return (
    <Paper
      variant="outlined"
      sx={{
        px: 2,
        py: 1,
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        flexWrap: 'wrap',
        bgcolor: 'action.selected',
      }}
    >
      <Typography variant="body2" sx={{ mr: 'auto' }}>
        {selected.length} selected
      </Typography>

      <LinkButtons
        selected={selected}
        busy={busy}
        onLinkRefund={onLinkRefund}
        onLinkTransfer={onLinkTransfer}
      />

      <Button size="small" onClick={onClear}>
        Clear
      </Button>
    </Paper>
  )
}
