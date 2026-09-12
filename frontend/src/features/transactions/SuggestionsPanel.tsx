import { useState } from 'react'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import Paper from '@mui/material/Paper'
import TablePagination from '@mui/material/TablePagination'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import type { ReactNode } from 'react'
import { useFittingRows } from '../../hooks/useFittingRows'
import { usePageOnWheel } from '../../hooks/usePageOnWheel'

/** Unstretched height of one suggestion row, which is a single truncated line. */
const SUGGESTION_ROW_HEIGHT = 40

/** Space between two rows; the list's row gap, kept here so the height below agrees with it. */
const SUGGESTION_GAP = 4

/**
 * Height of the pagination bar, tightened from MUI's 52. The list above it is sized from the rows
 * asked for rather than measured against the bar, so this only shortens the card — and the card has
 * to give those pixels back: three working lists sit below it on the categorize page, and at a
 * 864px window the pair of panels is within a few pixels of what that page can spare.
 */
const PAGINATION_HEIGHT = 44

/**
 * The list is a fixed slice of the page rather than a content-sized box. That matters: the row
 * count comes from measuring this element, so a content-sized one would shrink to whatever it last
 * rendered and never grow back past a single row. The height is exactly the rows asked for, so the
 * list ends where the last row does rather than leaving a gap under it.
 */
function listHeight(rows: number): string {
  return `${rows * SUGGESTION_ROW_HEIGHT + (rows - 1) * SUGGESTION_GAP}px`
}

interface SuggestionsPanelProps<T> {
  title: string
  /** What the suggestions mean, shown on demand: as a tooltip it costs the row no height. */
  hint: string
  /** Rows one page may show. Callers pass fewer on a short window, where three would not fit. */
  maxRows: number
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
 * and clicking a row (rather than its Apply button) shows the transactions it refers to. Two of
 * these sit side by side, so the header keeps to one line and the explanation moved into a tooltip.
 */
export function SuggestionsPanel<T>({
  title,
  hint,
  maxRows,
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
    max: maxRows,
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
    <Paper variant="outlined" sx={{ borderRadius: 3, px: 2.5, py: 1.5, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, minWidth: 0 }}>
          <Typography variant="h6" component="h3" noWrap>
            {title}
          </Typography>
          <Tooltip title={hint}>
            <InfoOutlined fontSize="small" sx={{ color: 'text.secondary', flexShrink: 0 }} />
          </Tooltip>
        </Box>
        <Button
          variant="outlined"
          onClick={onApplyAll}
          disabled={busy}
          sx={{ flexShrink: 0, whiteSpace: 'nowrap' }}
        >
          Apply all
        </Button>
      </Box>
      <Box
        ref={containerRef}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: `${SUGGESTION_GAP}px`,
          height: listHeight(maxRows),
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
          sx={{ flexShrink: 0, '.MuiTablePagination-toolbar': { minHeight: PAGINATION_HEIGHT } }}
        />
      ) : null}
    </Paper>
  )
}
