import { useState } from 'react'
import Add from '@mui/icons-material/Add'
import Delete from '@mui/icons-material/Delete'
import PlayArrow from '@mui/icons-material/PlayArrow'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Snackbar from '@mui/material/Snackbar'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { CategoryPresentation } from '../../api/queries'
import { uncategorizedColor, useScheme } from '../../theme'
import { useApplyMerchantRules, useCreateMerchantRule, useDeleteMerchantRule, useMerchantRules } from './hooks'

interface MerchantDefaultsProps {
  categories: CategoryPresentation[]
}

/** Lists merchant→category defaults and applies them to the uncategorized history. */
export function MerchantDefaults({ categories }: MerchantDefaultsProps) {
  const scheme = useScheme()
  const rules = useMerchantRules()
  const create = useCreateMerchantRule()
  const remove = useDeleteMerchantRule()
  const apply = useApplyMerchantRules()

  const [merchant, setMerchant] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [notice, setNotice] = useState<string | null>(null)

  const categoryById = new Map(categories.map((category) => [category.id, category]))

  const save = () => {
    if (!merchant.trim() || categoryId === '') return
    create.mutate({ merchant: merchant.trim(), categoryId: Number(categoryId) })
    setMerchant('')
    setCategoryId('')
  }

  const mutationError = (create.error ?? apply.error) as Error | null

  const runApply = async () => {
    try {
      const result = await apply.mutateAsync()
      setNotice(`Applied ${result.applied} transaction${result.applied === 1 ? '' : 's'}.`)
    } catch {
      // error surfaced by the alert above
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h6" component="h3">
            Defaults by counterparty
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Auto-tag future imports; apply to history manually.
          </Typography>
        </Box>
        <Button size="small" startIcon={<PlayArrow />} onClick={runApply} disabled={apply.isPending}>
          Apply to uncategorized
        </Button>
      </Box>

      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
        {rules.isLoading ? (
          <Typography variant="body2" color="text.secondary">
            Loading…
          </Typography>
        ) : (rules.data ?? []).length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No defaults yet. Select one uncategorized row and check “remember counterparty”.
          </Typography>
        ) : (
          (rules.data ?? []).map((rule) => {
            const category = categoryById.get(rule.categoryId)
            const color = category?.color ?? rule.color ?? uncategorizedColor[scheme]
            return (
              <Box key={rule.id} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 0.75, px: 1, borderRadius: 1.5, '&:hover': { bgcolor: 'action.hover' } }}>
                <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: color, flexShrink: 0 }} />
                <Typography variant="body2" sx={{ flexGrow: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {rule.merchant}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  → {category?.name ?? `Category ${rule.categoryId}`}
                </Typography>
                <IconButton size="small" onClick={() => remove.mutate(rule.id)} aria-label={`Delete default for ${rule.merchant}`}>
                  <Delete fontSize="small" />
                </IconButton>
              </Box>
            )
          })
        )}
      </Box>

      <Box sx={{ display: 'flex', gap: 1, pt: 1 }}>
        <TextField
          size="small"
          placeholder="Counterparty (e.g. Example Merchant)"
          value={merchant}
          onChange={(event) => setMerchant(event.target.value)}
          sx={{ flexGrow: 1, minWidth: 160 }}
        />
        <Select
          size="small"
          displayEmpty
          value={categoryId}
          onChange={(event) => setCategoryId(String(event.target.value))}
          renderValue={() => (categoryId === '' ? 'Category…' : '')}
          sx={{ minWidth: 150 }}
        >
          {categories.map((category) => (
            <MenuItem key={category.id} value={String(category.id)}>
              {category.name}
            </MenuItem>
          ))}
        </Select>
        <Button size="small" variant="contained" startIcon={<Add />} onClick={save} disabled={!merchant.trim() || categoryId === '' || create.isPending}>
          Add
        </Button>
      </Box>

      <Snackbar
        open={notice !== null}
        autoHideDuration={4000}
        onClose={() => setNotice(null)}
        message={notice}
      />
    </Paper>
  )
}
