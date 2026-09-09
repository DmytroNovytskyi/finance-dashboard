import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Add from '@mui/icons-material/Add'
import Edit from '@mui/icons-material/Edit'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Snackbar from '@mui/material/Snackbar'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { accountsApi } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'
import type { AccountKind } from '../../types'

const KIND_OPTIONS: { value: AccountKind; label: string }[] = [
  { value: 'PERSONAL', label: 'Personal' },
  { value: 'BUSINESS', label: 'Business' },
]

interface AccountForm {
  name: string
  currency: string
  kind: AccountKind | null
  accountNumber: string
}

/** List accounts and let the user set each one's personal/business type or add new ones. */
export function AccountsPage() {
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: queryKeys.accounts, queryFn: accountsApi.list })
  const [editor, setEditor] = useState<AccountForm | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
  }

  const setKind = useMutation({
    mutationFn: ({ id, kind }: { id: number; kind: AccountKind | null }) => accountsApi.update(id, { kind }),
    onSuccess: invalidate,
  })

  const saveAccount = useMutation({
    mutationFn: (form: AccountForm & { id?: number }) =>
      form.id === undefined
        ? accountsApi.create({
            name: form.name,
            currency: form.currency.toUpperCase(),
            kind: form.kind ?? undefined,
            accountNumber: form.accountNumber || null,
          })
        : accountsApi.update(form.id, {
            name: form.name,
            currency: form.currency.toUpperCase(),
            kind: form.kind ?? undefined,
            accountNumber: form.accountNumber || null,
          }),
    onSuccess: () => {
      invalidate()
      setEditor(null)
      setEditingId(null)
      setNotice('Account saved.')
    },
  })

  const mutationError = (setKind.error ?? saveAccount.error) as Error | null
  const rows = accounts.data ?? []
  const untyped = rows.filter((account) => account.kind === null)

  const submit = () => {
    if (!editor || !editor.name.trim()) return
    saveAccount.mutate({ ...editor, id: editingId ?? undefined })
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Accounts
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Set each account&apos;s type — the personal/business split in the overview follows it.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<Add />}
          onClick={() => {
            setEditingId(null)
            setEditor({ name: '', currency: 'PLN', kind: null, accountNumber: '' })
          }}
        >
          New account
        </Button>
      </Box>

      {untyped.length > 0 ? (
        <Alert severity="info">
          {untyped.length} account{untyped.length === 1 ? '' : 's'} without a type — pick Personal or Business below to include them in the split.
        </Alert>
      ) : null}

      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      <Paper variant="outlined" sx={{ borderRadius: 3, overflow: 'hidden' }}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Account</TableCell>
                <TableCell sx={{ width: 110 }}>Currency</TableCell>
                <TableCell sx={{ width: 200 }}>Type</TableCell>
                <TableCell>Account number</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 && !accounts.isLoading ? (
                <TableRow>
                  <TableCell colSpan={4}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      No accounts yet. Create one to start importing.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((account) => (
                  <TableRow key={account.id} hover>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {account.name}
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() => {
                            setEditingId(account.id)
                            setEditor({ name: account.name, currency: account.currency, kind: account.kind, accountNumber: account.accountNumber ?? '' })
                          }}
                          aria-label={`Edit ${account.name}`}
                        >
                          <Edit fontSize="small" />
                        </IconButton>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{account.currency}</Typography>
                    </TableCell>
                    <TableCell>
                      <KindSelect value={account.kind} onChange={(kind) => setKind.mutate({ id: account.id, kind })} />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color={account.accountNumber ? 'text.secondary' : 'text.disabled'}>
                        {account.accountNumber ?? '—'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Dialog open={editor !== null} onClose={() => setEditor(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{editingId === null ? 'New account' : 'Edit account'}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              autoFocus
              label="Name"
              value={editor?.name ?? ''}
              onChange={(event) => setEditor({ ...(editor as AccountForm), name: event.target.value })}
              fullWidth
              size="small"
            />
            <TextField
              label="Currency"
              value={editor?.currency ?? ''}
              onChange={(event) => setEditor({ ...(editor as AccountForm), currency: event.target.value.toUpperCase().slice(0, 3) })}
              fullWidth
              size="small"
              disabled={editingId !== null}
              helperText={editingId !== null ? 'Set when the account is created; not editable afterwards.' : 'Set at creation — it comes from the statements of this account.'}
            />
            <KindSelect value={editor?.kind ?? null} onChange={(kind) => setEditor({ ...(editor as AccountForm), kind })} />
            <TextField
              label="Account number (IBAN)"
              value={editor?.accountNumber ?? ''}
              onChange={(event) => setEditor({ ...(editor as AccountForm), accountNumber: event.target.value })}
              fullWidth
              size="small"
              required
              helperText={editor?.accountNumber.trim() ? undefined : 'Required — used to identify the account.'}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditor(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={submit}
            disabled={!editor?.name.trim() || !editor?.currency.trim() || !editor?.accountNumber.trim() || saveAccount.isPending}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={notice !== null} autoHideDuration={4000} onClose={() => setNotice(null)} message={notice} />
    </Box>
  )
}

function KindSelect({ value, onChange }: { value: AccountKind | null; onChange: (kind: AccountKind | null) => void }) {
  return (
    <Select
      size="small"
      displayEmpty
      value={value ?? ''}
      onChange={(event) => onChange((event.target.value as AccountKind | '') || null)}
      renderValue={() => (value === null ? 'No type' : value === 'PERSONAL' ? 'Personal' : 'Business')}
      sx={{ minWidth: 150 }}
    >
      <MenuItem value="">No type</MenuItem>
      {KIND_OPTIONS.map((option) => (
        <MenuItem key={option.value} value={option.value}>
          {option.label}
        </MenuItem>
      ))}
    </Select>
  )
}
