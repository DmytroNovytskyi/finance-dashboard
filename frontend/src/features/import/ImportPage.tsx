import { useRef, useState, type DragEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Delete from '@mui/icons-material/Delete'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import FileUpload from '@mui/icons-material/FileUpload'
import Grid from '@mui/material/Grid'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { statementsApi } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'
import { useAccounts, useAccountsById } from '../../api/queries'
import { formatDate, formatDateTime, formatInteger } from '../../lib/format'

/** Uploads a bank statement PDF for one account and lists the imported statements. */
export function ImportPage() {
  const queryClient = useQueryClient()
  const accountsQuery = useAccounts()
  const accountsById = useAccountsById()
  const accounts = accountsQuery.data ?? []

  const [accountId, setAccountId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const statementsQuery = useQuery({ queryKey: queryKeys.statements, queryFn: statementsApi.list })
  const [toDelete, setToDelete] = useState<{ id: number; fileName: string } | null>(null)

  const importMutation = useMutation({
    mutationFn: () => statementsApi.importPdf(Number(accountId), file as File),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.statements })
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
      queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
      setFile(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => statementsApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.statements })
      queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
      queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
      setToDelete(null)
    },
  })

  const pickFile = (next: File | undefined) => {
    if (next) setFile(next)
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragOver(false)
    pickFile(event.dataTransfer.files[0])
  }

  const canImport = accountId !== '' && file !== null && !importMutation.isPending
  const result = importMutation.data
  const resultAlert = importMutation.isError
    ? { severity: 'error' as const, text: importMutation.error?.message ?? 'Import failed.' }
    : result
      ? result.alreadyImported
        ? { severity: 'info' as const, text: 'This file was already imported — nothing changed.' }
        : {
            severity: 'success' as const,
            text: `Imported ${result.imported} transactions${result.skipped ? ` (${result.skipped} duplicates skipped)` : ''}.`,
          }
      : null

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Import
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Upload a bank statement PDF for one of your accounts.
        </Typography>
      </Box>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 2, height: '100%' }}>
            <Typography variant="h6" component="h3">
              Upload statement
            </Typography>
            <TextField
              select
              fullWidth
              label="Account"
              value={accountId}
              onChange={(event) => setAccountId(event.target.value)}
              size="small"
            >
              {accounts.length === 0 ? <MenuItem disabled value="">No accounts yet</MenuItem> : null}
              {accounts.map((account) => (
                <MenuItem key={account.id} value={String(account.id)}>
                  {account.name} ({account.currency})
                </MenuItem>
              ))}
            </TextField>

            <Box
              onDragOver={(event) => {
                event.preventDefault()
                setDragOver(true)
              }}
              onDragLeave={() => setDragOver(false)}
              onDrop={onDrop}
              onClick={() => fileInput.current?.click()}
              sx={{
                border: '1.5px dashed',
                borderColor: dragOver ? 'primary.main' : 'divider',
                borderRadius: 2,
                py: 4,
                px: 2,
                textAlign: 'center',
                cursor: 'pointer',
                bgcolor: dragOver ? 'action.hover' : 'transparent',
                transition: 'border-color 120ms',
              }}
            >
              <FileUpload color="action" sx={{ mb: 0.5 }} />
              <Typography variant="body2">{file ? file.name : 'Choose a PDF or drop it here'}</Typography>
              <Typography variant="caption" color="text.disabled">
                {file ? `${formatInteger(Math.round(file.size / 1024))} KB` : 'Bank Pekao statement (PDF)'}
              </Typography>
              <input
                ref={fileInput}
                type="file"
                accept="application/pdf,.pdf"
                hidden
                onChange={(event) => pickFile(event.target.files?.[0])}
              />
            </Box>

            {resultAlert ? <Alert severity={resultAlert.severity}>{resultAlert.text}</Alert> : null}

            <Button
              variant="contained"
              startIcon={importMutation.isPending ? <CircularProgress size={18} color="inherit" /> : <FileUpload />}
              disabled={!canImport}
              onClick={() => importMutation.mutate()}
            >
              {importMutation.isPending ? 'Importing…' : 'Import'}
            </Button>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Paper variant="outlined" sx={{ borderRadius: 3, overflow: 'hidden' }}>
            <Box sx={{ px: 2.5, py: 2 }}>
              <Typography variant="h6" component="h3">
                Imported statements
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Deleting a statement lets you re-import its file.
              </Typography>
            </Box>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>File</TableCell>
                    <TableCell>Account</TableCell>
                    <TableCell>Period</TableCell>
                    <TableCell align="right">Rows</TableCell>
                    <TableCell>Imported</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {statementsQuery.isLoading ? (
                    <TableRow>
                      <TableCell colSpan={6}>
                        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                          <CircularProgress size={24} />
                        </Box>
                      </TableCell>
                    </TableRow>
                  ) : (statementsQuery.data ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6}>
                        <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                          No statements imported yet.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    (statementsQuery.data ?? []).map((statement) => {
                      const account = accountsById.get(statement.accountId)
                      return (
                        <TableRow key={statement.id} hover>
                          <TableCell sx={{ maxWidth: 260 }}>
                            <Typography variant="body2" noWrap>
                              {statement.fileName ?? statement.bank}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2" color="text.secondary">
                              {account?.name ?? `Account ${statement.accountId}`}
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>
                            {statement.periodStart || statement.periodEnd
                              ? `${statement.periodStart ? formatDate(statement.periodStart) : '…'} – ${statement.periodEnd ? formatDate(statement.periodEnd) : '…'}`
                              : '—'}
                          </TableCell>
                          <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                            {formatInteger(statement.transactionCount)}
                          </TableCell>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(statement.importedAt)}</TableCell>
                          <TableCell align="right">
                            <IconButton
                              size="small"
                              onClick={() => setToDelete({ id: statement.id, fileName: statement.fileName ?? statement.bank })}
                              aria-label={`Delete ${statement.fileName ?? statement.bank}`}
                            >
                              <Delete fontSize="small" />
                            </IconButton>
                          </TableCell>
                        </TableRow>
                      )
                    })
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </Grid>
      </Grid>

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete statement</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Delete “{toDelete?.fileName}” and its {toDelete ? formatInteger(statementsQuery.data?.find((item) => item.id === toDelete.id)?.transactionCount ?? 0) : ''}{' '}
            stored transactions? A surviving transfer leg is un-paired, and the file can be imported again.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setToDelete(null)}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => {
              if (toDelete) deleteMutation.mutate(toDelete.id)
            }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
