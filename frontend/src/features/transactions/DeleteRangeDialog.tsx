import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import MenuItem from '@mui/material/MenuItem'
import TextField from '@mui/material/TextField'
import type { AccountPresentation } from '../../api/queries'

interface DeleteRangeDialogProps {
  open: boolean
  busy: boolean
  error: string | null
  accounts: AccountPresentation[]
  onClose: () => void
  onConfirm: (from: string, to: string, accountId?: number) => void
}

/** Collects an inclusive date range (optionally one account) for the delete-by-range action. */
export function DeleteRangeDialog({ open, busy, error, accounts, onClose, onConfirm }: DeleteRangeDialogProps) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [accountId, setAccountId] = useState('')

  const valid = from !== '' && to !== '' && from <= to

  const close = () => {
    if (!busy) onClose()
  }

  const confirm = () => {
    if (!valid) return
    const account = accountId === '' ? undefined : Number(accountId)
    onConfirm(from, to, account)
  }

  return (
    <Dialog open={open} onClose={close} maxWidth="sm" fullWidth>
      <DialogTitle>Delete transactions in a range</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Removes every transaction dated within the inclusive range. A surviving leg of an
          internal transfer is un-paired back to its natural type, and a statement left empty is
          deleted so its file can be imported again. This cannot be undone.
        </DialogContentText>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
          <TextField
            type="date"
            label="From"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
            size="small"
          />
          <TextField
            type="date"
            label="To"
            value={to}
            onChange={(event) => setTo(event.target.value)}
            size="small"
          />
        </Box>
        <TextField
          select
          fullWidth
          label="Account"
          value={accountId}
          onChange={(event) => setAccountId(event.target.value)}
          size="small"
          sx={{ mt: 2 }}
        >
          <MenuItem value="">All accounts</MenuItem>
          {accounts.map((account) => (
            <MenuItem key={account.id} value={String(account.id)}>
              {account.name} ({account.currency})
            </MenuItem>
          ))}
        </TextField>
        {error ? (
          <DialogContentText color="error" sx={{ mt: 1 }}>
            {error}
          </DialogContentText>
        ) : null}
      </DialogContent>
      <DialogActions>
        <Button onClick={close} disabled={busy}>
          Cancel
        </Button>
        <Button color="error" variant="contained" disabled={!valid || busy} onClick={confirm}>
          Delete range
        </Button>
      </DialogActions>
    </Dialog>
  )
}
