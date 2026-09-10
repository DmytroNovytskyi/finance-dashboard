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
import { useCreateCategory, useDeleteCategory, useUncategorizeCategory, useUpdateCategory } from './hooks'

interface CategoryManagerProps {
  categories: CategoryPresentation[]
}

const EMPTY_COLOR = '#607d8b'

interface EditorState {
  open: boolean
  id?: number
  name: string
  color: string
}

/** Add, rename, recolor, and delete spending categories. */
export function CategoryManager({ categories }: CategoryManagerProps) {
  const create = useCreateCategory()
  const update = useUpdateCategory()
  const remove = useDeleteCategory()
  const unlink = useUncategorizeCategory()

  const [editor, setEditor] = useState<EditorState>({ open: false, name: '', color: EMPTY_COLOR })
  const [confirmDelete, setConfirmDelete] = useState<{ id: number; name: string } | null>(null)

  const mutationError = (create.error ?? update.error ?? remove.error ?? unlink.error) as Error | null

  const [page, setPage] = useState(0)
  const { containerRef, rows: perPage } = useFittingRows(categories.length, { rowSelector: '[data-row]' })
  const maxPage = Math.max(0, Math.ceil(categories.length / perPage) - 1)
  const shownPage = Math.min(page, maxPage)
  const pageCategories = categories.slice(shownPage * perPage, shownPage * perPage + perPage)

  const openCreate = () => setEditor({ open: true, name: '', color: EMPTY_COLOR })
  const openEdit = (category: CategoryPresentation) => setEditor({ open: true, id: category.id, name: category.name, color: category.color })

  const save = () => {
    const { id, name, color } = editor
    if (!name.trim()) return
    if (id === undefined) {
      create.mutate({ name: name.trim(), color: color === EMPTY_COLOR ? undefined : color })
    } else {
      update.mutate({ id, name: name.trim(), color })
    }
    setEditor({ open: false, name: '', color: EMPTY_COLOR })
  }

  return (
    <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%', minHeight: 300 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
        <Box>
          <Typography variant="h6" component="h3">
            Categories
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {categories.length} groups
          </Typography>
        </Box>
        <Button size="small" startIcon={<Add />} onClick={openCreate}>
          New
        </Button>
      </Box>

      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      <Box
        ref={containerRef}
        sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, flex: 1, minHeight: 44, overflowY: 'auto' }}
      >
        {pageCategories.map((category) => (
          <Box
            key={category.id}
            data-row="category"
            sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minHeight: 40, py: 0.5, px: 1, borderRadius: 1.5, '&:hover': { bgcolor: 'action.hover' } }}
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

      <Dialog open={editor.open} onClose={() => setEditor({ ...editor, open: false })} maxWidth="xs" fullWidth>
        <DialogTitle>{editor.id === undefined ? 'New category' : 'Edit category'}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              autoFocus
              label="Name"
              value={editor.name}
              onChange={(event) => setEditor({ ...editor, name: event.target.value })}
              fullWidth
              size="small"
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Box component="label" sx={{ position: 'relative', width: 36, height: 36, borderRadius: '50%', bgcolor: editor.color, cursor: 'pointer', flexShrink: 0, display: 'inline-block' }}>
                <input
                  type="color"
                  value={editor.color}
                  onChange={(event) => setEditor({ ...editor, color: event.target.value })}
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
          <Button onClick={() => setEditor({ ...editor, open: false })}>Cancel</Button>
          <Button variant="contained" onClick={save} disabled={!editor.name.trim()}>
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
