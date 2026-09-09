import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { StatisticsByMonth } from '../../types'
import { amountColor, chartInk, useScheme } from '../../theme'
import { formatCompact, formatMonthKey, formatMoneyMagnitude } from '../../lib/format'
import { ChartTooltipCard } from '../../components/ChartTooltip'
import { EmptyState } from '../../components/EmptyState'
import { SeriesLegend } from '../../components/SeriesLegend'

interface MonthlyTrendChartProps {
  data: StatisticsByMonth[]
  baseCurrency: string
}

interface TrendTooltipProps {
  active?: boolean
  payload?: { dataKey?: string | number; name?: string; value?: number | string; color?: string; payload?: StatisticsByMonth }[]
}

function TrendTooltip({ active, payload, baseCurrency }: TrendTooltipProps & { baseCurrency: string }) {
  if (!active || !payload?.length) return null
  const month = payload[0].payload?.month
  return (
    <ChartTooltipCard
      title={month ? formatMonthKey(month) : undefined}
      rows={payload.map((entry) => ({
        label: entry.name ?? String(entry.dataKey ?? ''),
        value: formatMoneyMagnitude(Number(entry.value), baseCurrency),
        color: entry.color,
      }))}
    />
  )
}

/** Grouped income/expense columns by month over the selected period. */
export function MonthlyTrendChart({ data, baseCurrency }: MonthlyTrendChartProps) {
  const scheme = useScheme()
  const ink = chartInk[scheme]
  const colors = amountColor[scheme]
  if (data.length === 0) {
    return <EmptyNotice />
  }
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barGap={2}>
        <CartesianGrid vertical={false} stroke={ink.grid} />
        <XAxis
          dataKey="month"
          tickFormatter={(month) => formatMonthKey(String(month))}
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
        <Bar dataKey="income" name="Income" fill={colors.income} maxBarSize={24} radius={[4, 4, 0, 0]} />
        <Bar dataKey="expense" name="Expense" fill={colors.expense} maxBarSize={24} radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

/** Legend + empty state live beside the chart title; defined here to keep the layout cohesive. */
export function TrendLegend() {
  const scheme = useScheme()
  const colors = amountColor[scheme]
  return <SeriesLegend items={[{ label: 'Income', color: colors.income }, { label: 'Expense', color: colors.expense }]} />
}

function EmptyNotice() {
  return <EmptyState title="No income or expenses in this period" hint="Pick a wider period to see the trend." />
}
