import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import Box from '@mui/material/Box'
import type { StatisticsByMerchant } from '../../types'
import { categoricalPalette, chartInk, useScheme } from '../../theme'
import { formatCompact, formatInteger, formatMoneyMagnitude } from '../../lib/format'
import { ChartTooltipCard } from '../../components/ChartTooltip'
import { EmptyState } from '../../components/EmptyState'

const MAX_ROWS = 7

interface TopMerchantsChartProps {
  topMerchants: StatisticsByMerchant[]
  baseCurrency: string
}

interface MerchantDatum {
  merchant: string
  count: number
  expense: number
}

interface MerchantTooltipProps {
  active?: boolean
  payload?: { payload?: MerchantDatum }[]
  baseCurrency: string
}

function MerchantTooltip({ active, payload, baseCurrency }: MerchantTooltipProps) {
  if (!active || !payload?.length) return null
  const row = payload[0].payload
  if (!row) return null
  return (
    <ChartTooltipCard
      title={row.merchant}
      rows={[
        { label: 'Transactions', value: formatInteger(row.count) },
        { label: 'Spend', value: formatMoneyMagnitude(row.expense, baseCurrency) },
      ]}
    />
  )
}

/** Spending by merchant, ranked — every bar carries the same series color. */
export function TopMerchantsChart({ topMerchants, baseCurrency }: TopMerchantsChartProps) {
  const scheme = useScheme()
  const ink = chartInk[scheme]
  const series = categoricalPalette[scheme][0]
  const data = topMerchants
    .filter((merchant) => merchant.expense > 0)
    .map((merchant) => ({ ...merchant, expense: Math.abs(merchant.expense) }))
    .sort((a, b) => a.expense - b.expense)
    .slice(-MAX_ROWS)
  if (data.length === 0) {
    return <EmptyState title="No merchant spending in this period" />
  }
  return (
    <Box sx={{ height: '100%' }}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 0, left: 0 }}>
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
          <Tooltip content={<MerchantTooltip baseCurrency={baseCurrency} />} cursor={{ fill: ink.grid, opacity: 0.35 }} />
          <Bar dataKey="expense" name="Spend" fill={series} radius={[0, 4, 4, 0]} maxBarSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </Box>
  )
}
