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
import { PageHeader, PageShell } from '../../components/PageLayout'
import type { Account, AccountKind } from '../../types'

const KIND_OPTIONS: { value: AccountKind; label: string }[] = [
  { value: 'PERSONAL', label: 'Personal' },
  { value: 'BUSINESS', label: 'Business' },
]

/** The account fields the user owns. Currency and account number come from the statements. */
interface AccountDraft {
  name: string
  kind: AccountKind | null
}

interface AccountEditor {
  account: Account
  draft: AccountDraft
}

/** List accounts; each one is created by its statements and removed with its last transaction. */
export function AccountsPage() {
  const queryClient = useQueryClient()
  const accounts = useQuery({ queryKey: queryKeys.accounts, queryFn: accountsApi.list })
  const [editor, setEditor] = useState<AccountEditor | null>(null)
  const [toDelete, setToDelete] = useState<Account | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.statements })
  }

  const setKind = useMutation({
    mutationFn: ({ id, name, kind }: { id: number; name: string; kind: AccountKind | null }) =>
      accountsApi.update(id, { name, kind }),
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
    mutationFn: ({ account, draft }: AccountEditor) =>
      accountsApi.update(account.id, { name: draft.name.trim(), kind: draft.kind }),
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
    if (!editor || !editor.draft.name.trim()) return
    saveAccount.mutate(editor)
  }

  return (
    <PageShell>
      <PageHeader
        title="Accounts"
        subtitle="Accounts appear when you import their statements and disappear once no transaction remains. Set each account's type — the personal/business split in the overview follows it."
      />

      {untyped.length > 0 ? (
        <Alert severity="info">
          {untyped.length} account{untyped.length === 1 ? '' : 's'} without a type — pick Personal or Business below to include them in the split.
        </Alert>
      ) : null}

      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      <Paper
        variant="outlined"
        sx={{
          borderRadius: 3,
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          flex: 1,
          minHeight: 240,
        }}
      >
        <TableContainer sx={{ flex: 1, minHeight: 0 }}>
          <Table size="small" stickyHeader>
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
                            setEditor({ account, draft: { name: account.name, kind: account.kind } })
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
                      <KindSelect
                        value={account.kind}
                        onChange={(kind) => setKind.mutate({ id: account.id, name: account.name, kind })}
                      />
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
              value={editor?.draft.name ?? ''}
              onChange={(event) => setEditor((current) => current && { ...current, draft: { ...current.draft, name: event.target.value } })}
              fullWidth
              size="small"
            />
            <KindSelect
              value={editor?.draft.kind ?? null}
              onChange={(kind) => setEditor((current) => current && { ...current, draft: { ...current.draft, kind } })}
            />
            <TextField
              label="Currency"
              value={editor?.account.currency ?? ''}
              fullWidth
              size="small"
              disabled
              helperText="Comes from the imported statements; not editable."
            />
            <TextField
              label="Account number (IBAN)"
              value={editor?.account.accountNumber ?? ''}
              fullWidth
              size="small"
              disabled
              helperText={
                editor?.account.accountNumber
                  ? 'Comes from the imported statements; matches later imports to this account.'
                  : 'The imported statements carry no account number for this account.'
              }
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditor(null)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={submit}
            disabled={!editor?.draft.name.trim() || saveAccount.isPending}
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
    </PageShell>
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
