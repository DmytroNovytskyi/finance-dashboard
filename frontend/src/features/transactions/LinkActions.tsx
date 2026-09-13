import type { ReactNode } from 'react'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { Transaction } from '../../types'

interface LinkActionsProps {
  selected: Transaction[]
  busy: boolean
  onLinkRefund: (ids: number[]) => void
  onLinkTransfer: (fromId: number, toId: number) => void
  onClear: () => void
}

/** The two pairing buttons alone, for a bar that already has a selection count and a clear. */
interface LinkButtonsProps {
  selected: Transaction[]
  busy: boolean
  onLinkRefund: (ids: number[]) => void
  onLinkTransfer: (fromId: number, toId: number) => void
}

/** What a selection has to look like to become an internal transfer, or the reason it cannot. */
interface TransferShape {
  from: Transaction
  to: Transaction
}

/** The fewest rows a refund can be made of. */
const MIN_REFUND_LEGS = 2

/**
 * Why the selection cannot be linked as a refund, or null when it can. A refund is any group of two
 * or more rows: the arithmetic, sign, account and currency rules are deliberately gone. They existed
 * to stop a group leaving the statistics with money unaccounted for, and the statistics no longer
 * drop a group — each one is folded back in as its net, so an unbalanced or one-sided group cannot
 * make the figures disagree with what actually moved.
 */
function refundProblem(selected: Transaction[]): string | null {
  return selected.length < MIN_REFUND_LEGS ? 'select at least two rows to link a refund' : null
}

/**
 * An amount in minor units. Sums of decimals drift in binary floating point — 14.45 + 23.27 +
 * 23.74 comes to 61.46000000000001 — so comparing totals as they are would reject a transfer whose
 * legs the backend accepts.
 */
function toMinor(value: number): number {
  return Math.round(value * 100)
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
  const refundReason = refundProblem(selected)
  const transfer = transferShape(selected)
  const transferReady = typeof transfer !== 'string'

  return (
    <>
      <Tooltip
        title={
          refundReason ??
          'Link the selection as one refund; the group counts once, as its net'
        }
      >
        <span>
          <Button
            size="small"
            variant="contained"
            disabled={refundReason !== null || busy}
            onClick={() => onLinkRefund(selected.map((transaction) => transaction.id))}
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

/**
 * Height of the bar in both of its states. Taller than either one's own content, so the bar takes
 * this height whether or not rows are selected and the table below it never moves.
 */
const BAR_HEIGHT = 48

/**
 * The bar's box. Every state draws inside this one, and the height is pinned rather than left to
 * the content — a bar that grows when a selection appears would push the table down and re-measure
 * the fitted row count, which is exactly the jump this bar is meant not to cause.
 */
function SelectionBar({ highlighted, children }: { highlighted: boolean; children: ReactNode }) {
  return (
    <Paper
      variant="outlined"
      sx={{
        px: 2,
        height: BAR_HEIGHT,
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        flexWrap: 'nowrap',
        bgcolor: highlighted ? 'action.selected' : 'transparent',
      }}
    >
      {children}
    </Paper>
  )
}

/**
 * Bar shown over the transactions table for the whole of edit mode. It keeps its place when nothing
 * is selected and says what selecting would do, so entering and leaving a selection never moves the
 * table underneath it.
 */
export function LinkActions({ selected, busy, onLinkRefund, onLinkTransfer, onClear }: LinkActionsProps) {
  if (selected.length === 0) {
    return (
      <SelectionBar highlighted={false}>
        <Typography variant="body2" color="text.secondary">
          Select rows to link a refund or an internal transfer.
        </Typography>
      </SelectionBar>
    )
  }

  return (
    <SelectionBar highlighted>
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
    </SelectionBar>
  )
}
