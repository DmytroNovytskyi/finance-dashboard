import { useRef, type MouseEvent as ReactMouseEvent } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import Box from '@mui/material/Box'
import MenuItem from '@mui/material/MenuItem'
import Select from '@mui/material/Select'
import type { StatisticsGranularity, StatisticsTrendPoint } from '../../types'
import { amountColor, chartInk, useScheme } from '../../theme'
import { formatCompact, formatDateRange, formatMoneyMagnitude, formatTrendBucket } from '../../lib/format'
import { ChartTooltipCard } from '../../components/ChartTooltip'
import { EmptyState } from '../../components/EmptyState'
import { SeriesLegend } from '../../components/SeriesLegend'

export const TREND_GRANULARITIES: StatisticsGranularity[] = ['day', 'week', 'month', 'quarter', 'year']

interface TrendChartProps {
  data: StatisticsTrendPoint[]
  baseCurrency: string
  granularity: StatisticsGranularity
  onSelect: (point: StatisticsTrendPoint) => void
}

interface TrendTooltipProps {
  active?: boolean
  payload?: { name?: string; value?: number | string; color?: string; payload?: StatisticsTrendPoint }[]
  baseCurrency: string
}

function TrendTooltip({ active, payload, baseCurrency }: TrendTooltipProps) {
  if (!active || !payload?.length) return null
  const point = payload[0].payload
  return (
    <ChartTooltipCard
      title={point ? formatDateRange(point.start, point.end) : undefined}
      rows={payload.map((entry) => ({
        label: entry.name ?? '',
        value: formatMoneyMagnitude(Number(entry.value), baseCurrency),
        color: entry.color,
      }))}
    />
  )
}

/** Income/expense columns bucketed by the chosen granularity; bars focus the other charts. */
export function TrendChart({ data, baseCurrency, granularity, onSelect }: TrendChartProps) {
  const scheme = useScheme()
  const ink = chartInk[scheme]
  const colors = amountColor[scheme]
  const plotRef = useRef<HTMLDivElement>(null)

  if (data.length === 0) {
    return <EmptyState title="No income or expenses in this period" hint="Pick a wider period to see the trend." />
  }

  // Category cells are evenly spaced across the plot after the fixed-width y axis, so a click
  // anywhere in a cell (not just on the bar) maps to that bucket.
  const onPlotMouseDown = (event: ReactMouseEvent<HTMLDivElement>) => {
    const node = plotRef.current
    if (!node) return
    const rect = node.getBoundingClientRect()
    const x = event.clientX - rect.left
    const y = event.clientY - rect.top
    const axisWidth = 46
    const rightPad = 8
    const plotWidth = rect.width - axisWidth - rightPad
    const n = data.length
    if (plotWidth <= 0 || n === 0 || x < axisWidth || x > axisWidth + plotWidth || y > rect.height - 14) return
    const index = Math.min(n - 1, Math.floor(((x - axisWidth) / plotWidth) * n))
    onSelect(data[index])
  }

  return (
    <div ref={plotRef} onMouseDown={onPlotMouseDown} style={{ width: '100%', height: '100%' }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2} barCategoryGap="35%">
          <CartesianGrid vertical={false} stroke={ink.grid} />
          <XAxis
            dataKey="start"
            tickFormatter={(start) => formatTrendBucket(String(start), granularity)}
            tick={{ fill: ink.textSecondary, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            tickFormatter={formatCompact}
            tick={{ fill: ink.textSecondary, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
            width={46}
          />
          <Tooltip content={<TrendTooltip baseCurrency={baseCurrency} />} cursor={{ fill: ink.grid, opacity: 0.35 }} />
          <Bar dataKey="income" name="Income" fill={colors.income} maxBarSize={16} radius={[4, 4, 0, 0]} />
          <Bar dataKey="expense" name="Expense" fill={colors.expense} maxBarSize={16} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

/** Granularity picker and the income/expense legend, for the chart card header. */
export function TrendControls({
  granularity,
  onGranularityChange,
}: {
  granularity: StatisticsGranularity
  onGranularityChange: (granularity: StatisticsGranularity) => void
}) {
  const scheme = useScheme()
  const colors = amountColor[scheme]
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
      <Select
        size="small"
        value={granularity}
        onChange={(event) => onGranularityChange(event.target.value as StatisticsGranularity)}
        aria-label="Trend granularity"
        sx={{ minWidth: 120, '.MuiSelect-select': { py: 0.75 } }}
      >
        {TREND_GRANULARITIES.map((option) => (
          <MenuItem key={option} value={option} sx={{ textTransform: 'capitalize' }}>
            {option}
          </MenuItem>
        ))}
      </Select>
      <SeriesLegend items={[{ label: 'Income', color: colors.income }, { label: 'Expense', color: colors.expense }]} />
    </Box>
  )
}
