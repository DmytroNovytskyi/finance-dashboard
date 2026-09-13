import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import Box from '@mui/material/Box'
import type { StatisticsByMerchant } from '../../types'
import { categoricalPalette, chartInk, useScheme } from '../../theme'
import { formatCompact, formatInteger, formatMoneyMagnitude } from '../../lib/format'
import { ChartTooltipCard } from '../../components/ChartTooltip'
import { EmptyState } from '../../components/EmptyState'
import type { Direction } from './MetricToggle'

const MAX_ROWS = 7

interface TopMerchantsChartProps {
  topMerchants: StatisticsByMerchant[]
  baseCurrency: string
  direction: Direction
  /** Called with the merchant behind a bar, so the chart can drill through like the donut does. */
  onSelect: (merchant: string) => void
}

interface MerchantDatum {
  merchant: string
  count: number
  value: number
}

interface MerchantTooltipProps {
  active?: boolean
  payload?: { payload?: MerchantDatum }[]
  baseCurrency: string
  direction: Direction
}

function MerchantTooltip({ active, payload, baseCurrency, direction }: MerchantTooltipProps) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  if (!row) return null
  return (
    <ChartTooltipCard
      title={row.merchant}
      rows={[
        { label: 'Transactions', value: formatInteger(row.count) },
        {
          label: direction === 'expense' ? 'Spend' : 'Income',
          value: formatMoneyMagnitude(row.value, baseCurrency),
        },
      ]}
    />
  )
}

/** Money by merchant, ranked — every bar carries the same series color. */
export function TopMerchantsChart({
  topMerchants,
  baseCurrency,
  direction,
  onSelect,
}: TopMerchantsChartProps) {
  const scheme = useScheme()
  const ink = chartInk[scheme]
  const series = categoricalPalette[scheme][0]
  const rows = topMerchants
    .filter((merchant) => merchant[direction] > 0)
    .map((merchant) => ({
      merchant: merchant.merchant,
      count: merchant.count,
      value: Math.abs(merchant[direction]),
    }))
    .sort((a, b) => a.value - b.value)
    .slice(-MAX_ROWS)
  if (rows.length === 0) {
    return (
      <EmptyState
        title={direction === 'expense' ? 'No merchant spending in this period' : 'No merchant income in this period'}
      />
    )
  }
  return (
    <Box sx={{ height: '100%' }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid horizontal={false} stroke={ink.grid} />
          <XAxis
            type="number"
            tickFormatter={formatCompact}
            tick={{ fill: ink.textSecondary, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            type="category"
            dataKey="merchant"
            reversed
            width={150}
            tickFormatter={(name) => (String(name).length > 26 ? `${String(name).slice(0, 25)}…` : String(name))}
            tick={{ fill: ink.textSecondary, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            content={<MerchantTooltip baseCurrency={baseCurrency} direction={direction} />}
            cursor={{ fill: ink.grid, opacity: 0.35 }}
          />
          <Bar
            dataKey="value"
            name={direction === 'expense' ? 'Spend' : 'Income'}
            fill={series}
            radius={[0, 4, 4, 0]}
            maxBarSize={18}
            cursor="pointer"
            isAnimationActive={false}
            onClick={(_entry: unknown, index: number) => {
              const row = rows[index]
              if (row) onSelect(row.merchant)
            }}
          />
        </BarChart>
      </ResponsiveContainer>
    </Box>
  )
}
