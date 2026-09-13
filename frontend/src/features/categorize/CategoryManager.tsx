import { useState } from 'react'
import Add from '@mui/icons-material/Add'
import Delete from '@mui/icons-material/Delete'
import LinkOff from '@mui/icons-material/LinkOff'
import Lock from '@mui/icons-material/Lock'
import TablePagination from '@mui/material/TablePagination'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Dialog from '@mui/material/Dialog'
import DialogActions from '@mui/material/DialogActions'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogTitle from '@mui/material/DialogTitle'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { CategoryPresentation } from '../../api/queries'
import { useFittingRows } from '../../hooks/useFittingRows'
import { usePageOnWheel } from '../../hooks/usePageOnWheel'
import { useCreateCategory, useDeleteCategory, useUncategorizeCategory, useUpdateCategory } from './hooks'

interface CategoryManagerProps {
  categories: CategoryPresentation[]
}

const EMPTY_COLOR = '#607d8b'

/** Height of the pagination bar, which the paged lists take out of their row space. */
const PAGINATION_HEIGHT = 48

/** Unstretched height of one category row; the rows share whatever height the card has left. */
const CATEGORY_ROW_HEIGHT = 40

/** The category the edit dialog is renaming or recolouring. */
interface EditState {
  id: number
  name: string
  color: string
}

/** Add, rename, recolor, and delete spending categories. */
export function CategoryManager({ categories }: CategoryManagerProps) {
  const create = useCreateCategory()
  const update = useUpdateCategory()
  const remove = useDeleteCategory()
  const unlink = useUncategorizeCategory()

  const [draft, setDraft] = useState({ name: '', color: EMPTY_COLOR })
  const [editing, setEditing] = useState<EditState | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<{ id: number; name: string } | null>(null)

  const mutationError = (create.error ?? update.error ?? remove.error ?? unlink.error) as Error | null

  const [page, setPage] = useState(0)
  const { containerRef, container, rows: perPage, rowHeight } = useFittingRows(categories.length, {
    rowSelector: '[data-row]',
    paginationSelector: '.MuiTablePagination-root',
    paginationHeight: PAGINATION_HEIGHT,
    naturalRowHeight: CATEGORY_ROW_HEIGHT,
  })
  const maxPage = Math.max(0, Math.ceil(categories.length / perPage) - 1)
  const shownPage = Math.min(page, maxPage)
  const pageCategories = categories.slice(shownPage * perPage, shownPage * perPage + perPage)

  usePageOnWheel(container, {
    onNext: () => setPage((current) => Math.min(current + 1, maxPage)),
    onPrevious: () => setPage((current) => Math.max(current - 1, 0)),
    canNext: shownPage < maxPage,
    canPrevious: shownPage > 0,
    enabled: maxPage > 0,
  })

  /**
   * Adds what the row at the foot of the card describes, then clears it for the next one. An
   * untouched colour is left out rather than sent as the placeholder, so a category created without
   * a preference still takes whatever colour the API assigns.
   */
  const createCategory = () => {
    if (!draft.name.trim()) return
    create.mutate({ name: draft.name.trim(), color: draft.color === EMPTY_COLOR ? undefined : draft.color })
    setDraft({ name: '', color: EMPTY_COLOR })
  }

  const openEdit = (category: CategoryPresentation) =>
    setEditing({ id: category.id, name: category.name, color: category.color })

  const saveEdit = () => {
    if (!editing || !editing.name.trim()) return
    update.mutate({ id: editing.id, name: editing.name.trim(), color: editing.color })
    setEditing(null)
  }

  return (
    <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%', minHeight: 300 }}>
      <Box>
        <Typography variant="h6" component="h3">
          Categories
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {categories.length} groups
        </Typography>
      </Box>

      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      <Box
        ref={containerRef}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.5,
          flex: 1,
          minHeight: CATEGORY_ROW_HEIGHT,
          overflowY: 'auto',
          overscrollBehaviorY: 'contain',
        }}
      >
        {pageCategories.map((category) => (
          <Box
            key={category.id}
            data-row="category"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              flex: '0 0 auto',
              height: rowHeight ?? CATEGORY_ROW_HEIGHT,
              minHeight: CATEGORY_ROW_HEIGHT,
              py: 0.5,
              px: 1,
              borderRadius: 1.5,
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            {category.system ? (
              <Box aria-hidden sx={{ width: 28, height: 28, borderRadius: '50%', bgcolor: category.color, flexShrink: 0 }} />
            ) : (
              <Box
                component="label"
                sx={{ position: 'relative', width: 28, height: 28, borderRadius: '50%', bgcolor: category.color, cursor: 'pointer', flexShrink: 0, display: 'inline-block' }}
                title="Change color"
              >
                <input
                  type="color"
                  value={category.color}
                  onChange={(event) => update.mutate({ id: category.id, color: event.target.value })}
                  aria-label={`Change color of ${category.name}`}
                  style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%', height: '100%' }}
                />
              </Box>
            )}
            <Typography
              variant="body2"
              onClick={category.system ? undefined : () => openEdit(category)}
              sx={{ flexGrow: 1, minWidth: 0, cursor: category.system ? 'default' : 'pointer', '&:hover': category.system ? {} : { textDecoration: 'underline' } }}
            >
              {category.name}
            </Typography>
            {category.system ? (
              <IconButton size="small" tabIndex={-1} aria-label="System category" sx={{ color: 'text.disabled' }}>
                <Lock fontSize="small" />
              </IconButton>
            ) : (
              <>
                <IconButton
                  size="small"
                  onClick={() => unlink.mutate(category.id)}
                  title="Unlink: clear this category from its transactions"
                  aria-label={`Unlink ${category.name}`}
                >
                  <LinkOff fontSize="small" />
                </IconButton>
                <IconButton size="small" onClick={() => setConfirmDelete({ id: category.id, name: category.name })} aria-label={`Delete ${category.name}`}>
                  <Delete fontSize="small" />
                </IconButton>
              </>
            )}
          </Box>
        ))}
      </Box>

      {categories.length > perPage ? (
        <TablePagination
          component="div"
          count={categories.length}
          page={shownPage}
          onPageChange={(_event, nextPage) => setPage(nextPage)}
          rowsPerPage={perPage}
          rowsPerPageOptions={[]}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} of ${count}`}
          sx={{ flexShrink: 0, '.MuiTablePagination-toolbar': { minHeight: 40 } }}
        />
      ) : null}

      <Box sx={{ display: 'flex', gap: 1, pt: 1 }}>
        <TextField
          size="small"
          placeholder="New category"
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          sx={{ flexGrow: 1, minWidth: 120 }}
        />
        <Box
          component="label"
          title="Colour"
          sx={{ position: 'relative', width: 40, height: 40, borderRadius: '50%', bgcolor: draft.color, cursor: 'pointer', flexShrink: 0, display: 'inline-block' }}
        >
          <input
            type="color"
            value={draft.color}
            onChange={(event) => setDraft({ ...draft, color: event.target.value })}
            aria-label="New category colour"
            style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%', height: '100%' }}
          />
        </Box>
        <IconButton
          onClick={createCategory}
          disabled={!draft.name.trim() || create.isPending}
          aria-label="Add category"
          color="primary"
        >
          <Add />
        </IconButton>
      </Box>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Edit category</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              autoFocus
              label="Name"
              value={editing?.name ?? ''}
              onChange={(event) =>
                setEditing((current) => (current ? { ...current, name: event.target.value } : current))
              }
              fullWidth
              size="small"
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Box component="label" sx={{ position: 'relative', width: 36, height: 36, borderRadius: '50%', bgcolor: editing?.color ?? EMPTY_COLOR, cursor: 'pointer', flexShrink: 0, display: 'inline-block' }}>
                <input
                  type="color"
                  value={editing?.color ?? EMPTY_COLOR}
                  onChange={(event) =>
                    setEditing((current) => (current ? { ...current, color: event.target.value } : current))
                  }
                  style={{ position: 'absolute', inset: 0, opacity: 0, cursor: 'pointer', width: '100%', height: '100%' }}
                  aria-label="Color"
                />
              </Box>
              <Typography variant="body2" color="text.secondary">
                Color
              </Typography>
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>Cancel</Button>
          <Button variant="contained" onClick={saveEdit} disabled={!editing?.name.trim()}>
            Save
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={confirmDelete !== null} onClose={() => setConfirmDelete(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Delete category</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Delete “{confirmDelete?.name}”? Its transactions become uncategorized. This cannot be undone.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(null)}>Cancel</Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => {
              if (confirmDelete) remove.mutate(confirmDelete.id)
              setConfirmDelete(null)
            }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Paper>
  )
}
