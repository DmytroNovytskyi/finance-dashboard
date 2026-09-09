import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Delete from '@mui/icons-material/Delete'
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
import type { Account, AccountKind } from '../../types'

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

/** List accounts; each one is created by its statements and removed with its last transaction. */
export function AccountsPage() {
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: queryKeys.accounts, queryFn: accountsApi.list })
  const [editor, setEditor] = useState<{ id: number; form: AccountForm } | null>(null)
  const [toDelete, setToDelete] = useState<Account | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.statements })
  }

  const setKind = useMutation({
    mutationFn: ({ id, kind }: { id: number; kind: AccountKind | null }) => accountsApi.update(id, { kind }),
    onSuccess: invalidate,
  })

  const deleteAccount = useMutation({
    mutationFn: (id: number) => accountsApi.remove(id),
    onSuccess: (result) => {
      invalidate()
      setToDelete(null)
      setNotice(result.count > 0 ? `Deleted the account and ${result.count} transactions.` : 'Deleted the account.')
    },
  })

  const saveAccount = useMutation({
    mutationFn: ({ id, form }: { id: number; form: AccountForm }) =>
      accountsApi.update(id, {
        name: form.name.trim(),
        currency: form.currency.toUpperCase(),
        kind: form.kind ?? undefined,
        accountNumber: form.accountNumber || null,
      }),
    onSuccess: () => {
      invalidate()
      setEditor(null)
      setNotice('Account saved.')
    },
  })

  const mutationError = (setKind.error ?? saveAccount.error ?? deleteAccount.error) as Error | null
  const rows = accounts.data ?? []
  const untyped = rows.filter((account) => account.kind === null)

  const submit = () => {
    if (!editor || !editor.form.name.trim()) return
    saveAccount.mutate(editor)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Accounts
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Accounts appear when you import their statements and disappear once no transaction
          remains. Set each account&apos;s type — the personal/business split in the overview
          follows it.
        </Typography>
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
                <TableCell>Account number</TableCell>
                <TableCell sx={{ width: 90 }}>Currency</TableCell>
                <TableCell sx={{ width: 200 }}>Type</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 && !accounts.isLoading ? (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                      No accounts yet. Import a statement and its account will appear here.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                rows.map((account) => (
                  <TableRow key={account.id} hover>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                          {account.name}
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() =>
                            setEditor({
                              id: account.id,
                              form: {
                                name: account.name,
                                currency: account.currency,
                                kind: account.kind,
                                accountNumber: account.accountNumber ?? '',
                              },
                            })
                          }
                          aria-label={`Edit ${account.name}`}
                        >
                          <Edit fontSize="small" />
                        </IconButton>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" color={account.accountNumber ? 'text.secondary' : 'text.disabled'}>
                        {account.accountNumber ?? '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{account.currency}</Typography>
                    </TableCell>
                    <TableCell>
                      <KindSelect value={account.kind} onChange={(kind) => setKind.mutate({ id: account.id, kind })} />
                    </TableCell>
                    <TableCell align="right">
                      <IconButton size="small" onClick={() => setToDelete(account)} aria-label={`Delete ${account.name}`}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Dialog open={editor !== null} onClose={() => setEditor(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Edit account</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              autoFocus
              label="Name"
              value={editor?.form.name ?? ''}
              onChange={(event) => setEditor((current) => current && { ...current, form: { ...current.form, name: event.target.value } })}
              fullWidth
              size="small"
            />
            <TextField
              label="Currency"
              value={editor?.form.currency ?? ''}
              fullWidth
              size="small"
              disabled
              helperText="Comes from the imported statements; not editable."
            />
            <KindSelect value={editor?.form.kind ?? null} onChange={(kind) => setEditor((current) => current && { ...current, form: { ...current.form, kind } })} />
            <TextField
              label="Account number (IBAN)"
              value={editor?.form.accountNumber ?? ''}
              onChange={(event) => setEditor((current) => current && { ...current, form: { ...current.form, accountNumber: event.target.value } })}
              fullWidth
              size="small"
              helperText={
                editor?.form.accountNumber.trim()
                  ? 'Used to match future statements to this account.'
                  : 'Empty — set it so future statements of this account are matched to it.'
              }
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditor(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={submit}
            disabled={!editor?.form.name.trim() || !editor.form.currency.trim() || saveAccount.isPending}
          >
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete account</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            This removes “{toDelete?.name}” and all of its statements and transactions, including any
            surviving leg of a paired transfer. The account is recreated if you import one of its
            statements again.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setToDelete(null)}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            disabled={deleteAccount.isPending}
            onClick={() => {
              if (toDelete) deleteAccount.mutate(toDelete.id)
            }}
          >
            Delete
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
