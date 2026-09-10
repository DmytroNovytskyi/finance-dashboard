import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Paper from '@mui/material/Paper'
import TablePagination from '@mui/material/TablePagination'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { useFittingRows } from '../../hooks/useFittingRows'
import { usePageOnWheel } from '../../hooks/usePageOnWheel'

/** Unstretched height of one suggestion row, which is a single truncated line. */
const SUGGESTION_ROW_HEIGHT = 40

/**
 * What the list gives up for the bar, which is MUI's default toolbar height. Reserving less lets
 * the list and the bar together overrun the budget above by the difference the moment the bar
 * appears. Pinning the toolbar's min-height does not help: the bar's own content already sets its
 * height, so the override would be inert while the reservation stayed wrong.
 */
const PAGINATION_HEIGHT = 52

/**
 * The list is a fixed slice of the page rather than a content-sized box. That matters: the row
 * count comes from measuring this element, so a content-sized one would shrink to whatever it last
 * rendered and never grow back past a single row.
 */
const LIST_HEIGHT = `calc(min(280px, 26dvh) - ${PAGINATION_HEIGHT}px)`

interface SuggestionsPanelProps<T> {
  title: string
  subtitle: string
  suggestions: T[]
  busy: boolean
  rowKey: (suggestion: T) => string
  /** The text block of one row; the Apply button is added by the panel. */
  renderRow: (suggestion: T) => ReactNode
  onApplyAll: () => void
  onApply: (suggestion: T) => void
  /** Clicking the row itself (not its Apply button) shows the transactions it refers to. */
  onSelect: (suggestion: T) => void
}

/**
 * Pending auto-detected pairs of rows: review them and apply each one or all at once. Nothing is
 * linked until it is applied here. The list pages itself so a long backlog cannot stretch the card,
 * and clicking a row (rather than its Apply button) shows the transactions it refers to.
 */
export function SuggestionsPanel<T>({
  title,
  subtitle,
  suggestions,
  busy,
  rowKey,
  renderRow,
  onApplyAll,
  onApply,
  onSelect,
}: SuggestionsPanelProps<T>) {
  const [page, setPage] = useState(0)
  const { containerRef, container, rows: perPage } = useFittingRows(suggestions.length, {
    rowSelector: '[data-row]',
    naturalRowHeight: SUGGESTION_ROW_HEIGHT,
  })
  const maxPage = Math.max(0, Math.ceil(suggestions.length / perPage) - 1)
  const shownPage = Math.min(page, maxPage)
  const pageSuggestions = suggestions.slice(shownPage * perPage, shownPage * perPage + perPage)

  usePageOnWheel(container, {
    onNext: () => setPage((current) => Math.min(current + 1, maxPage)),
    onPrevious: () => setPage((current) => Math.max(current - 1, 0)),
    canNext: shownPage < maxPage,
    canPrevious: shownPage > 0,
    enabled: maxPage > 0,
  })

  return (
    <Paper variant="outlined" sx={{ borderRadius: 3, px: 2.5, py: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap' }}>
        <Box>
          <Typography variant="h6" component="h3">
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        </Box>
        <Button variant="outlined" onClick={onApplyAll} disabled={busy}>
          Apply all
        </Button>
      </Box>
      <Box
        ref={containerRef}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.5,
          height: LIST_HEIGHT,
          overflowY: 'auto',
          overscrollBehaviorY: 'contain',
        }}
      >
        {pageSuggestions.map((suggestion) => (
          <Box
            key={rowKey(suggestion)}
            data-row="suggestion"
            role="button"
            tabIndex={0}
            onClick={() => onSelect(suggestion)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                onSelect(suggestion)
              }
            }}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              minHeight: SUGGESTION_ROW_HEIGHT,
              px: 1,
              borderRadius: 1.5,
              cursor: 'pointer',
              '&:hover': { bgcolor: 'action.hover' },
            }}
          >
            <Box sx={{ minWidth: 0, flexGrow: 1 }}>{renderRow(suggestion)}</Box>
            <Button
              size="small"
              disabled={busy}
              onClick={(event) => {
                event.stopPropagation()
                onApply(suggestion)
              }}
            >
              Apply
            </Button>
          </Box>
        ))}
      </Box>

      {suggestions.length > perPage ? (
        <TablePagination
          component="div"
          count={suggestions.length}
          page={shownPage}
          onPageChange={(_event, nextPage) => setPage(nextPage)}
          rowsPerPage={perPage}
          rowsPerPageOptions={[]}
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} of ${count}`}
          sx={{ flexShrink: 0 }}
        />
      ) : null}
    </Paper>
  )
}
