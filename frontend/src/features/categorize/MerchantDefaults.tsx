import { useState } from 'react'
import Add from '@mui/icons-material/Add'
import Delete from '@mui/icons-material/Delete'
import PlayArrow from '@mui/icons-material/PlayArrow'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import IconButton from '@mui/material/IconButton'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import Select from '@mui/material/Select'
import Snackbar from '@mui/material/Snackbar'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import type { CategoryPresentation } from '../../api/queries'
import { uncategorizedColor, useScheme } from '../../theme'
import { useApplyMerchantRule, useCreateMerchantRule, useDeleteMerchantRule, useMerchantRules } from './hooks'

interface MerchantDefaultsProps {
  categories: CategoryPresentation[]
}

/** Lists merchant→category defaults and lets each be applied to matching uncategorized rows. */
export function MerchantDefaults({ categories }: MerchantDefaultsProps) {
  const scheme = useScheme()
  const rules = useMerchantRules()
  const create = useCreateMerchantRule()
  const remove = useDeleteMerchantRule()
  const applyOne = useApplyMerchantRule()

  const [merchant, setMerchant] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [notice, setNotice] = useState<string | null>(null)

  const categoryById = new Map(categories.map((category) => [category.id, category]))
  const selectedCategory = categoryId !== '' ? categoryById.get(Number(categoryId)) : undefined

  const save = () => {
    if (!merchant.trim() || categoryId === '') return
    create.mutate({ merchant: merchant.trim(), categoryId: Number(categoryId) })
    setMerchant('')
    setCategoryId('')
  }

  const runApply = async (id: number, label: string) => {
    try {
      const result = await applyOne.mutateAsync(id)
      setNotice(`Applied ${label} to ${result.applied} uncategorized row${result.applied === 1 ? '' : 's'}.`)
    } catch {
      // error surfaced by the alert above
    }
  }

  const mutationError = (create.error ?? applyOne.error ?? remove.error) as Error | null

  return (
    <Paper variant="outlined" sx={{ p: 2.5, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 1.5, height: '100%' }}>
      <Box>
        <Typography variant="h6" component="h3">
          Defaults by counterparty
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Auto-tag future imports. Deleting a default also un-categorizes the rows it tagged.
        </Typography>
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
                <Box sx={{ minWidth: 0, flexGrow: 1 }}>
                  <Typography variant="body2" noWrap title={rule.merchant}>
                    {rule.merchant}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" noWrap>
                    {category?.name ?? `Category ${rule.categoryId}`}
                  </Typography>
                </Box>
                <IconButton size="small" title="Apply to matching history" onClick={() => runApply(rule.id, rule.merchant)} aria-label={`Apply default for ${rule.merchant}`}>
                  <PlayArrow fontSize="small" />
                </IconButton>
                <IconButton size="small" onClick={() => remove.mutate(rule.id)} title="Delete: stops auto-tagging and un-categorizes matching rows" aria-label={`Delete default for ${rule.merchant}`}>
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
          renderValue={() => selectedCategory?.name ?? 'Category…'}
          sx={{ minWidth: 150 }}
        >
          {categories.map((category) => (
            <MenuItem key={category.id} value={String(category.id)}>
              {category.name}
            </MenuItem>
          ))}
        </Select>
        <IconButton onClick={save} disabled={!merchant.trim() || categoryId === '' || create.isPending} aria-label="Add default" color="primary">
          <Add />
        </IconButton>
      </Box>

      <Snackbar open={notice !== null} autoHideDuration={4000} onClose={() => setNotice(null)} message={notice} />
    </Paper>
  )
}
