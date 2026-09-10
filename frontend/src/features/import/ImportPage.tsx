import { useMemo, useRef, useState, type DragEvent } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import CircularProgress from '@mui/material/CircularProgress'
import Close from '@mui/icons-material/Close'
import Delete from '@mui/icons-material/Delete'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import FileUpload from '@mui/icons-material/FileUpload'
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
import TablePagination from '@mui/material/TablePagination'
import TableRow from '@mui/material/TableRow'
import TableSortLabel from '@mui/material/TableSortLabel'
import Typography from '@mui/material/Typography'
import { statementsApi, type StatementSortKey } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'
import { useAccounts, useAccountsById } from '../../api/queries'
import { PageHeader, PageShell } from '../../components/PageLayout'
import { useFittingRows } from '../../hooks/useFittingRows'
import { usePageOnWheel } from '../../hooks/usePageOnWheel'
import { formatDate, formatDateTime, formatInteger } from '../../lib/format'


/** Current server-side sort of the statements list. */
interface StatementSort {
  key: StatementSortKey
  dir: 'asc' | 'desc'
}

const DEFAULT_STATEMENT_SORT: StatementSort = { key: 'imported', dir: 'desc' }

/** Account-filter sentinel; real account ids start at 1, so 0 means "no account restriction". */
const ALL_ACCOUNTS = 0

interface ImportOutcome {
  ok: number
  imported: number
  skipped: number
  alreadyImported: number
  failed: number
}

/** Uploads bank statement PDFs in batches; imported files can be selected and deleted together. */
export function ImportPage() {
  const queryClient = useQueryClient()
  const accountsById = useAccountsById()

  const [files, setFiles] = useState<File[]>([])
  const [busy, setBusy] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const [outcome, setOutcome] = useState<ImportOutcome | null>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const accounts = useAccounts().data ?? []
  const [sort, setSort] = useState<StatementSort>(DEFAULT_STATEMENT_SORT)
  const [accountFilter, setAccountFilter] = useState(ALL_ACCOUNTS)
  const listParams = useMemo(
    () => ({
      sort: sort.key,
      order: sort.dir,
      accountId: accountFilter === ALL_ACCOUNTS ? undefined : accountFilter,
    }),
    [sort, accountFilter],
  )
  const statementsQuery = useQuery({
    queryKey: queryKeys.statementList(listParams),
    queryFn: () => statementsApi.list(listParams),
    placeholderData: keepPreviousData,
  })
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [deleting, setDeleting] = useState(false)
  const [confirmDeleteMany, setConfirmDeleteMany] = useState(false)
  const [deleteNotice, setDeleteNotice] = useState<string | null>(null)

  const [page, setPage] = useState(0)
  const { containerRef, container, rows: fittingRows } = useFittingRows(statementsQuery.data?.length ?? 0, {
    rowSelector: 'tbody tr',
    reservedSelector: 'thead',
  })

  const changeSort = (key: StatementSortKey) => {
    setSort((current) =>
      current.key === key ? { key, dir: current.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' },
    )
    setPage(0)
  }

  const sortProps = (key: StatementSortKey) => ({
    active: sort.key === key,
    direction: (sort.key === key ? sort.dir : 'asc') as 'asc' | 'desc',
    onClick: () => changeSort(key),
  })

  /** Changing the account drops the selection so a filtered-out row cannot be deleted unseen. */
  const changeAccountFilter = (value: number) => {
    setAccountFilter(value)
    setSelected(new Set())
    setPage(0)
  }

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.statements })
    queryClient.invalidateQueries({ queryKey: queryKeys.transactions.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.statistics.root })
    queryClient.invalidateQueries({ queryKey: queryKeys.accounts })
  }

  const runImport = async () => {
    if (files.length === 0 || busy) return
    setBusy(true)
    setFailure(null)
    setOutcome(null)
    const result: ImportOutcome = { ok: 0, imported: 0, skipped: 0, alreadyImported: 0, failed: 0 }
    const errors: string[] = []
    for (const file of files) {
      try {
        const response = await statementsApi.importPdf(file)
        if (response.alreadyImported) {
          result.alreadyImported += 1
        } else {
          result.ok += 1
          result.imported += response.imported
          result.skipped += response.skipped
        }
      } catch (error) {
        result.failed += 1
        errors.push(`${file.name}: ${error instanceof Error ? error.message : 'failed'}`)
      }
    }
    setOutcome(result)
    if (result.failed > 0) {
      setFailure(errors.join('; '))
    }
    invalidateAll()
    setFiles([])
    setPage(0)
    setBusy(false)
  }

  const choose = (next: FileList | File[]) => {
    const picked = Array.from(next)
    if (picked.length > 0) {
      setFiles(picked)
      setPage(0)
    }
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragOver(false)
    choose(event.dataTransfer.files)
  }

  const allStatements = statementsQuery.data ?? []
  const total = allStatements.length
  const rowsPerPage = fittingRows
  const maxPage = Math.max(0, Math.ceil(total / rowsPerPage) - 1)
  const shownPage = Math.min(page, maxPage)

  usePageOnWheel(container, {
    onNext: () => setPage((current) => Math.min(current + 1, maxPage)),
    onPrevious: () => setPage((current) => Math.max(current - 1, 0)),
    canNext: shownPage < maxPage,
    canPrevious: shownPage > 0,
    enabled: maxPage > 0,
  })
  const shownRows = allStatements.slice(shownPage * rowsPerPage, shownPage * rowsPerPage + rowsPerPage)
  const canImport = files.length > 0 && !busy

  const toggle = (id: number) =>
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })

  const pageIds = shownRows.map((row) => row.id)
  const allPageSelected = pageIds.length > 0 && pageIds.every((id) => selected.has(id))
  const somePageSelected = pageIds.some((id) => selected.has(id))

  const togglePage = () => {
    setSelected((current) => {
      const next = new Set(current)
      if (allPageSelected) {
        pageIds.forEach((id) => next.delete(id))
      } else {
        pageIds.forEach((id) => next.add(id))
      }
      return next
    })
  }

  const deleteSelected = async () => {
    setConfirmDeleteMany(false)
    setDeleting(true)
    setFailure(null)
    const errors: string[] = []
    let deleted = 0
    for (const id of selected) {
      try {
        await statementsApi.remove(id)
        deleted += 1
      } catch (error) {
        errors.push(error instanceof Error ? error.message : 'delete failed')
      }
    }
    if (errors.length > 0) {
      setFailure(errors.join('; '))
    }
    setDeleteNotice(`Deleted ${deleted} statement${deleted === 1 ? '' : 's'}.`)
    setSelected(new Set())
    invalidateAll()
    setDeleting(false)
  }

  const outcomeSummary = outcome
    ? outcome.alreadyImported > 0 && outcome.imported === 0 && outcome.failed === 0
      ? 'All selected files were already imported.'
      : [
          outcome.imported > 0 ? `Imported ${formatInteger(outcome.imported)} transactions from ${outcome.ok} file${outcome.ok === 1 ? '' : 's'}` : null,
          outcome.skipped > 0 ? `${formatInteger(outcome.skipped)} duplicate${outcome.skipped === 1 ? '' : 's'} skipped` : null,
          outcome.alreadyImported > 0 ? `${outcome.alreadyImported} file${outcome.alreadyImported === 1 ? '' : 's'} already imported` : null,
          outcome.failed > 0 ? `${outcome.failed} failed` : null,
        ]
          .filter(Boolean)
          .join('; ')
    : null

  const snackbarOpen = outcome !== null || deleteNotice !== null
  const snackbarMessage = deleteNotice ?? outcomeSummary

  return (
    <PageShell>
      <PageHeader
        title="Import"
        subtitle="Upload bank statement PDFs in one go. Each account is read from its file and reused or created automatically."
      />

      <Box
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
          gap: 2,
          flex: { xs: 'none', md: 1 },
          minHeight: 0,
        }}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', flex: { xs: 'none', md: '1 1 30%' }, minWidth: 0, minHeight: 0 }}>
          <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 2, height: '100%' }}>
            <Typography variant="h6" component="h3">
              Upload statements
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Pick several files at once; they import in order.
            </Typography>

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
              <Typography variant="body2">{files.length > 0 ? `${files.length} file${files.length === 1 ? '' : 's'} ready` : 'Choose PDFs or drop them here'}</Typography>
              <Typography variant="caption" color="text.disabled">
                {files.length > 0 ? files.map((file) => file.name).join(', ') : 'Bank Pekao statements (PDF)'}
              </Typography>
              <input
                ref={fileInput}
                type="file"
                accept="application/pdf,.pdf"
                multiple
                hidden
                onChange={(event) => choose(event.target.files ?? [])}
              />
            </Box>

            {files.length > 0 ? (
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <Typography variant="body2" color="text.secondary">
                  {`${formatInteger(Math.round(files.reduce((sum, file) => sum + file.size, 0) / 1024))} KB total`}
                </Typography>
                <IconButton size="small" onClick={() => { setFiles([]); setOutcome(null); setFailure(null) }} aria-label="Clear selection" disabled={busy}>
                  <Close fontSize="small" />
                </IconButton>
              </Box>
            ) : null}

            {failure ? <Alert severity="error">{failure}</Alert> : null}

            <Button
              variant="contained"
              startIcon={busy ? <CircularProgress size={18} color="inherit" /> : <FileUpload />}
              disabled={!canImport}
              onClick={runImport}
            >
              {busy ? 'Importing…' : `Import${files.length > 1 ? ` ${files.length} files` : ''}`}
            </Button>
          </Paper>
        </Box>

        <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 62%', minWidth: 0, minHeight: 0 }}>
          <Paper
            variant="outlined"
            sx={{
              borderRadius: 3,
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              flex: 1,
              minHeight: 320,
            }}
          >
            <Box sx={{ px: 2.5, py: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
              <Box>
                <Typography variant="h6" component="h3">
                  Imported statements
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Select rows to delete several at once.
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Select
                  size="small"
                  displayEmpty
                  value={accountFilter}
                  onChange={(event) => changeAccountFilter(Number(event.target.value))}
                  renderValue={(value) =>
                    value === ALL_ACCOUNTS
                      ? 'All accounts'
                      : accountsById.get(value)?.name ?? `Account ${value}`
                  }
                  aria-label="Filter statements by account"
                  sx={{ minWidth: 180, '.MuiSelect-select': { py: 0.75 } }}
                >
                  <MenuItem value={ALL_ACCOUNTS}>All accounts</MenuItem>
                  {accounts.map((account) => (
                    <MenuItem key={account.id} value={account.id}>
                      {account.name}
                    </MenuItem>
                  ))}
                </Select>
                {selected.size > 0 ? (
                  <Button
                    size="small"
                    color="error"
                    variant="outlined"
                    startIcon={deleting ? <CircularProgress size={16} color="inherit" /> : <Delete />}
                    onClick={() => setConfirmDeleteMany(true)}
                    disabled={deleting}
                  >
                    Delete {selected.size}
                  </Button>
                ) : null}
              </Box>
            </Box>
            <TableContainer ref={containerRef} sx={{ flex: 1, minHeight: 0, overscrollBehaviorY: 'contain' }}>
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        size="small"
                        checked={allPageSelected}
                        indeterminate={!allPageSelected && somePageSelected}
                        onChange={togglePage}
                        aria-label="Select all on this page"
                      />
                    </TableCell>
                    <TableCell>
                      <TableSortLabel {...sortProps('file')}>File</TableSortLabel>
                    </TableCell>
                    <TableCell>
                      <TableSortLabel {...sortProps('account')}>Account</TableSortLabel>
                    </TableCell>
                    <TableCell>
                      <TableSortLabel {...sortProps('period')}>Period</TableSortLabel>
                    </TableCell>
                    <TableCell align="right">Rows</TableCell>
                    <TableCell>
                      <TableSortLabel {...sortProps('imported')}>Imported</TableSortLabel>
                    </TableCell>
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
                  ) : total === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6}>
                        <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
                          {accountFilter === ALL_ACCOUNTS
                            ? 'No statements imported yet.'
                            : 'No statements for this account.'}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    shownRows.map((statement) => {
                      const account = accountsById.get(statement.accountId)
                      return (
                        <TableRow key={statement.id} hover selected={selected.has(statement.id)}>
                          <TableCell padding="checkbox">
                            <Checkbox
                              size="small"
                              checked={selected.has(statement.id)}
                              onChange={() => toggle(statement.id)}
                              aria-label={`Select ${statement.fileName ?? statement.bank}`}
                            />
                          </TableCell>
                          <TableCell sx={{ maxWidth: 240 }}>
                            <Typography variant="body2" noWrap>
                              {statement.fileName ?? statement.bank}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 160 }}>
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
                        </TableRow>
                      )
                    })
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            {total > 0 ? (
              <TablePagination
                component="div"
                count={total}
                page={shownPage}
                onPageChange={(_event, nextPage) => setPage(nextPage)}
                rowsPerPage={rowsPerPage}
                rowsPerPageOptions={[]}
                labelDisplayedRows={({ from, to, count }) => `${from}–${to} of ${count}`}
              />
            ) : null}
          </Paper>
        </Box>
      </Box>

      <Snackbar open={snackbarOpen} autoHideDuration={6000} onClose={() => { setOutcome(null); setDeleteNotice(null) }} message={snackbarMessage} />

      <Dialog open={confirmDeleteMany} onClose={() => setConfirmDeleteMany(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete statements</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Delete {selected.size} selected statement{selected.size === 1 ? '' : 's'} and their stored transactions? An account left with nothing is removed too.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDeleteMany(false)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={() => { void deleteSelected() }}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </PageShell>
  )
}
