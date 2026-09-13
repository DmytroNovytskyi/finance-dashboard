import { useMemo } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import type { StatisticsByCategory } from '../../types'
import { categoricalPalette, uncategorizedColor, useScheme } from '../../theme'
import { formatMoneyMagnitude } from '../../lib/format'
import { ChartTooltipCard } from '../../components/ChartTooltip'
import { EmptyState } from '../../components/EmptyState'
import type { Direction } from './MetricToggle'

export interface DonutRow {
  categoryId: number | null
  name: string
  value: number
  color: string
}

interface CategoryDonutProps {
  byCategory: StatisticsByCategory[]
  baseCurrency: string
  direction: Direction
  onSelect: (row: DonutRow) => void
}

/**
 * Resolves one stable color per category: its own color, gray for the uncategorized bucket. Only
 * categories with money on the chosen side are kept, so switching to income drops the categories
 * that only ever paid out and a refund group, which is income when its net is positive, appears
 * just like any other.
 */
function buildRows(
  byCategory: StatisticsByCategory[],
  scheme: 'light' | 'dark',
  direction: Direction,
): DonutRow[] {
  const withMoney = byCategory.filter((category) => category[direction] > 0)
  const uncolored = withMoney
    .filter((category) => !category.color && category.categoryId !== null)
    .sort((a, b) => (a.categoryName ?? '').localeCompare(b.categoryName ?? ''))
  const fallbackIndex = new Map(uncolored.map((category, index) => [category.categoryId, index]))
  const rows = withMoney.map((category) => {
    const isUncategorized = category.categoryId === null
    const color = isUncategorized
      ? uncategorizedColor[scheme]
      : (category.color ?? categoricalPalette[scheme][(fallbackIndex.get(category.categoryId) ?? 0) % 8])
    return {
      categoryId: category.categoryId,
      name: category.categoryName ?? '(uncategorized)',
      value: Math.abs(category[direction]),
      color,
    }
  })
  return rows.sort((a, b) => b.value - a.value)
}

/** By category: a donut for the big buckets beside a labeled, valued list, for either direction. */
export function CategoryDonut({ byCategory, baseCurrency, direction, onSelect }: CategoryDonutProps) {
  const scheme = useScheme()
  const rows = useMemo(() => buildRows(byCategory, scheme, direction), [byCategory, scheme, direction])
  if (rows.length === 0) {
    return direction === 'expense' ? (
      <EmptyState title="No expenses in this period" hint="Transactions with a category will appear here." />
    ) : (
      <EmptyState title="No income in this period" hint="Money coming in with a category will appear here." />
    )
  }
  const total = rows.reduce((sum, row) => sum + row.value, 0)

  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center', height: '100%' }}>
      {rows.length >= 2 ? (
        <Box sx={{ flex: '1 1 220px', minWidth: 0, height: '100%', overflow: 'hidden' }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={rows}
                dataKey="value"
                nameKey="name"
                cx="50%"
                cy="50%"
                innerRadius="62%"
                outerRadius="90%"
                paddingAngle={2}
                cornerRadius={4}
                onClick={(_, index) => onSelect(rows[index])}
                stroke="none"
              >
                {rows.map((row) => (
                  <Cell key={row.categoryId ?? 'uncategorized'} fill={row.color} tabIndex={0} />
                ))}
              </Pie>
              <Tooltip
                content={<DonutTooltip baseCurrency={baseCurrency} />}
              />
            </PieChart>
          </ResponsiveContainer>
        </Box>
      ) : (
        <Box sx={{ flex: '1 1 220px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Typography variant="body2" color="text.secondary">
            One bucket — see the list for the value.
          </Typography>
        </Box>
      )}
      <Box
        component="ul"
        sx={{
          flex: '1 1 260px',
          minWidth: 220,
          minHeight: 0,
          maxHeight: '100%',
          overflowY: 'auto',
          m: 0,
          p: 0,
          listStyle: 'none',
          display: 'flex',
          flexDirection: 'column',
          gap: 0.5,
        }}
      >
        {rows.map((row) => {
          const share = Math.round((row.value / total) * 100)
          return (
            <Box
              component="li"
              key={row.categoryId ?? 'uncategorized'}
              onClick={() => onSelect(row)}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                borderRadius: 1.5,
                px: 1,
                py: 0.5,
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' },
              }}
            >
              <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: row.color, flexShrink: 0 }} />
              <Typography variant="body2" sx={{ flexGrow: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {row.name}
              </Typography>
              <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatMoneyMagnitude(row.value, baseCurrency)}
              </Typography>
              <Typography variant="caption" color="text.disabled" sx={{ width: 34, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                {share}%
              </Typography>
            </Box>
          )
        })}
      </Box>
    </Box>
  )
}

interface DonutTooltipProps {
  active?: boolean
  payload?: { payload?: DonutRow; value?: number | string }[]
  baseCurrency: string
}

function DonutTooltip({ active, payload, baseCurrency }: DonutTooltipProps) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  return (
    <ChartTooltipCard
      rows={[{ label: row?.name ?? '', value: formatMoneyMagnitude(Number(row?.value ?? payload[0].value), baseCurrency), color: row?.color }]}
    />
  )
}
