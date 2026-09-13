import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import Grid from '@mui/material/Grid'
import MenuItem from '@mui/material/MenuItem'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useCategories } from '../../api/queries'
import { statisticsApi } from '../../api/endpoints'
import { queryKeys } from '../../api/keys'
import type { DateRange } from '../../lib/date'
import {
  formatCompact,
  formatDate,
  formatDateRange,
  formatInteger,
  formatMoney,
  formatMoneyMagnitude,
  formatShortDate,
  formatTrendBucket,
} from '../../lib/format'
import { amountColor, chartInk, useScheme } from '../../theme'
import { ChartCard } from '../../components/ChartCard'
import { ChartTooltipCard } from '../../components/ChartTooltip'
import { EmptyState } from '../../components/EmptyState'
import { PageHeader, PageScroll, PageShell } from '../../components/PageLayout'
import { SeriesLegend } from '../../components/SeriesLegend'
import { StatementFreshness } from '../../components/StatementFreshness'
import type { CategoryPresentation } from '../../api/queries'
import type { StatisticsCategorySeriesPoint, StatisticsGranularity, StatisticsTrendPoint } from '../../types'
import { useDisplayCurrency, type DisplayCurrency } from '../preferences/displayCurrency'
import {
  granularityLabel,
  useStatisticsPreferences,
  type PeriodSelection,
  type TrendGranularity,
} from '../preferences/statisticsPreferences'
import { PeriodSelector } from '../overview/PeriodSelector'

/** Kept under its original name so the saved category chart choice survives the rename. */
const TRENDS_STORAGE_KEY = 'finance-dashboard.categories.v1'

interface StoredTrendsState {
  categories?: unknown
}

/**
 * Reads the chosen category charts back. The period and the granularity are not stored here: both
 * statistics pages share them through {@link useStatisticsPreferences}.
 */
function loadTrendsState(): number[] {
  try {
    const raw = window.localStorage.getItem(TRENDS_STORAGE_KEY)
    if (!raw) return []
    const stored = JSON.parse(raw) as StoredTrendsState
    return Array.isArray(stored.categories)
      ? stored.categories.filter((id): id is number => typeof id === 'number')
      : []
  } catch {
    return []
  }
}

interface CategoryStats {
  count: number
  spent: number
  received: number
  avgSpent: number | null
  avgReceived: number | null
}

/** Averages and totals over the category's individual transactions in the period. */
function seriesStatsOf(points: StatisticsCategorySeriesPoint[]): CategoryStats {
  let count = 0
  let spent = 0
  let received = 0
  let expenseRows = 0
  let incomeRows = 0
  for (const point of points) {
    count++
    if (point.amount < 0) {
      spent -= point.amount
      expenseRows++
    } else if (point.amount > 0) {
      received += point.amount
      incomeRows++
    }
  }
  return {
    count,
    spent,
    received,
    avgSpent: expenseRows > 0 ? spent / expenseRows : null,
    avgReceived: incomeRows > 0 ? received / incomeRows : null,
  }
}

/** Averages and totals across the time buckets (the granularity level) rather than transactions. */
function bucketStatsOf(buckets: StatisticsTrendPoint[]): CategoryStats {
  let spent = 0
  let received = 0
  let expenseBuckets = 0
  let incomeBuckets = 0
  for (const bucket of buckets) {
    if (bucket.expense > 0) {
      spent += bucket.expense
      expenseBuckets++
    }
    if (bucket.income > 0) {
      received += bucket.income
      incomeBuckets++
    }
  }
  return {
    count: buckets.length,
    spent,
    received,
    avgSpent: expenseBuckets > 0 ? spent / expenseBuckets : null,
    avgReceived: incomeBuckets > 0 ? received / incomeBuckets : null,
  }
}

/** One line chart per category at a page-level granularity (per transaction or time buckets). */
export function TrendsPage() {
  const categories = useCategories()
  const { displayCurrency } = useDisplayCurrency()
  const { period, granularity, setPeriod, setGranularity } = useStatisticsPreferences()
  const [categoryIds, setCategoryIds] = useState<number[]>(loadTrendsState)
  const [pending, setPending] = useState('')

  const byId = useMemo(() => new Map(categories.map((category) => [category.id, category])), [categories])
  const selected = categoryIds.map((id) => byId.get(id)).filter((category): category is CategoryPresentation => !!category)
  const addable = categories.filter((category) => !category.system && !categoryIds.includes(category.id))

  const changePeriod = (next: PeriodSelection) => setPeriod(next)
  const changeGranularity = (next: TrendGranularity) => setGranularity(next)

  const addCategory = (id: string) => {
    const numeric = Number(id)
    if (id !== '' && !categoryIds.includes(numeric)) {
      setCategoryIds((current) => [...current, numeric])
    }
    setPending('')
  }
  const removeCategory = (id: number) => setCategoryIds((current) => current.filter((value) => value !== id))

  useEffect(() => {
    try {
      window.localStorage.setItem(
        TRENDS_STORAGE_KEY,
        JSON.stringify({ categories: categoryIds }),
      )
    } catch {
      /* storage unavailable: keep the selection for this session only */
    }
  }, [categoryIds])

  return (
    <PageShell>
      <PageHeader
        title="Trends"
        subtitle="A chart per category; the period and granularity below drive every chart."
      />

      <PeriodSelector
        period={period}
        granularity={granularity}
        onChange={changePeriod}
        onGranularityChange={changeGranularity}
      />

      <StatementFreshness />

      <Paper
        variant="outlined"
        sx={{ px: 2, py: 1.5, display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}
      >
        <TextField
          select
          size="small"
          label="Add category"
          value={pending}
          onChange={(event) => addCategory(event.target.value)}
          slotProps={{
            select: { displayEmpty: true, renderValue: () => 'Choose a category…' },
            inputLabel: { shrink: true },
          }}
          sx={{ minWidth: 220 }}
        >
          {addable.length === 0 ? (
            <MenuItem value="" disabled>
              No more categories to add
            </MenuItem>
          ) : (
            addable.map((category) => (
              <MenuItem key={category.id} value={String(category.id)}>
                <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1 }}>
                  <Box aria-hidden sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: category.color }} />
                  {category.name}
                </Box>
              </MenuItem>
            ))
          )}
        </TextField>

        {selected.map((category) => (
          <Chip
            key={category.id}
            label={category.name}
            onDelete={() => removeCategory(category.id)}
            variant="outlined"
            sx={{
              height: 40,
              borderRadius: 2,
              pl: 0.5,
              '.MuiChip-label': { px: 1.5, fontSize: '0.9375rem' },
              '.MuiChip-deleteIcon': { fontSize: 20, mr: 1 },
            }}
          />
        ))}
      </Paper>

      <PageScroll>
        {selected.length === 0 ? (
          <Paper variant="outlined" sx={{ p: 4, borderRadius: 3, textAlign: 'center' }}>
            <Typography color="text.secondary">Add a category above to chart its income and expenses over time.</Typography>
          </Paper>
        ) : (
          <Grid container spacing={2}>
            {selected.map((category) => (
              <Grid key={category.id} size={12}>
                {granularity !== 'transaction' ? (
                  <CategoryTrendCard category={category} range={period.range} granularity={granularity} displayCurrency={displayCurrency} />
                ) : (
                  <CategorySeriesCard category={category} range={period.range} displayCurrency={displayCurrency} />
                )}
              </Grid>
            ))}
          </Grid>
        )}
      </PageScroll>
    </PageShell>
  )
}

interface SeriesRow {
  ts: number
  date: string
  income: number | null
  expense: number | null
}

interface StatItemData {
  label: string
  value: string
}

function StatItem({ label, value }: StatItemData) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.25 }}>
      <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.05 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>
        {value}
      </Typography>
    </Box>
  )
}

function StatStrip({ stats, currency, countLabel }: { stats: CategoryStats; currency: string; countLabel: string }) {
  const items: StatItemData[] = [{ label: countLabel, value: formatInteger(stats.count) }]
  if (stats.spent > 0) {
    items.push({ label: 'Total spent', value: formatMoney(stats.spent, currency) })
  }
  if (stats.received > 0) {
    items.push({ label: 'Total received', value: formatMoney(stats.received, currency) })
  }
  if (stats.avgSpent !== null) {
    items.push({ label: 'Avg expense', value: formatMoney(stats.avgSpent, currency) })
  }
  if (stats.avgReceived !== null) {
    items.push({ label: 'Avg income', value: formatMoney(stats.avgReceived, currency) })
  }
  return (
    <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
      {items.map((item) => (
        <StatItem key={item.label} label={item.label} value={item.value} />
      ))}
    </Box>
  )
}

/** One full-width chart for a category at "transaction" granularity: each transaction at its date. */
function CategorySeriesCard({
  category,
  range,
  displayCurrency,
}: {
  category: CategoryPresentation
  range: DateRange
  displayCurrency: DisplayCurrency
}) {
  const scheme = useScheme()
  const ink = chartInk[scheme]
  const colors = amountColor[scheme]
  const params = { from: range.from ?? undefined, to: range.to ?? undefined, displayCurrency }
  const query = useQuery({
    queryKey: queryKeys.statistics.categorySeries(category.id, params),
    queryFn: () => statisticsApi.categorySeries(category.id, params),
  })

  const points = query.data?.points ?? []
  const baseCurrency = query.data?.baseCurrency ?? 'PLN'
  const hasIncome = points.some((point) => point.amount > 0)
  const hasExpense = points.some((point) => point.amount < 0)
  const stats = seriesStatsOf(points)

  const rows = useMemo<SeriesRow[]>(
    () =>
      points.map((point) => ({
        ts: Date.parse(point.date),
        date: point.date,
        income: point.amount > 0 ? point.amount : null,
        expense: point.amount < 0 ? -point.amount : null,
      })),
    [points],
  )

  const legend = [
    hasIncome ? { label: 'Income', color: colors.income } : null,
    hasExpense ? { label: 'Expense', color: colors.expense } : null,
  ].filter((item): item is { label: string; color: string } => item !== null)

  return (
    <ChartCard
      title={category.name}
      subtitle={`in ${baseCurrency} · each point is one transaction`}
      action={legend.length > 1 ? <SeriesLegend items={legend} /> : null}
      meta={points.length > 0 ? <StatStrip stats={stats} currency={baseCurrency} countLabel="Transactions" /> : null}
      chartHeight={320}
    >
      {points.length === 0 ? (
        <EmptyState title={`Nothing for ${category.name} in this period`} hint="Pick a wider period or another category." />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={ink.grid} />
            <XAxis
              dataKey="ts"
              type="number"
              scale="time"
              domain={['auto', 'auto']}
              tickFormatter={(value: number) => formatShortDate(new Date(value).toISOString().slice(0, 10))}
              tick={{ fill: ink.textSecondary, fontSize: 12 }}
              axisLine={false}
              tickLine={false}
              minTickGap={40}
            />
            <YAxis
              tickFormatter={formatCompact}
              tick={{ fill: ink.textSecondary, fontSize: 12 }}
              axisLine={false}
              tickLine={false}
              width={46}
              domain={[0, 'auto']}
            />
            <Tooltip content={<SeriesTooltip baseCurrency={baseCurrency} />} cursor={{ stroke: ink.textSecondary, strokeDasharray: '3 3' }} />
            {hasExpense ? (
              <Line type="monotone" dataKey="expense" name="Expense" connectNulls stroke={colors.expense} strokeWidth={2} dot={{ r: 2.5 }} isAnimationActive={false} />
            ) : null}
            {hasIncome ? (
              <Line type="monotone" dataKey="income" name="Income" connectNulls stroke={colors.income} strokeWidth={2} dot={{ r: 2.5 }} isAnimationActive={false} />
            ) : null}
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

interface SeriesTooltipProps {
  active?: boolean
  payload?: { name?: string; color?: string; value?: number | string; payload?: StatisticsCategorySeriesPoint }[]
  baseCurrency: string
}

function SeriesTooltip({ active, payload, baseCurrency }: SeriesTooltipProps) {
  if (!active || !payload?.length) return null
  const rows = payload
    .filter((entry) => typeof entry.value === 'number')
    .map((entry) => ({
      label: entry.name ?? '',
      value: formatMoney(Number(entry.value), baseCurrency),
      color: entry.color,
    }))
  if (rows.length === 0) return null
  const date = payload[0]?.payload?.date
  return <ChartTooltipCard title={date ? formatDate(date) : undefined} rows={rows} />
}

/** One full-width chart for a category at a bucket granularity, mirroring the Overview trend look. */
function CategoryTrendCard({
  category,
  range,
  granularity,
  displayCurrency,
}: {
  category: CategoryPresentation
  range: DateRange
  granularity: StatisticsGranularity
  displayCurrency: DisplayCurrency
}) {
  const scheme = useScheme()
  const ink = chartInk[scheme]
  const colors = amountColor[scheme]
  const params = { from: range.from ?? undefined, to: range.to ?? undefined, granularity, displayCurrency }
  const query = useQuery({
    queryKey: queryKeys.statistics.categoryTrend(category.id, params),
    queryFn: () => statisticsApi.categoryTrend(category.id, params),
  })

  const data = query.data?.trend ?? []
  const baseCurrency = query.data?.baseCurrency ?? 'PLN'
  const hasIncome = data.some((point) => point.income !== 0)
  const hasExpense = data.some((point) => point.expense !== 0)
  const stats = bucketStatsOf(data)

  const legend = [
    hasIncome ? { label: 'Income', color: colors.income } : null,
    hasExpense ? { label: 'Expense', color: colors.expense } : null,
  ].filter((item): item is { label: string; color: string } => item !== null)

  return (
    <ChartCard
      title={category.name}
      subtitle={`in ${baseCurrency} · ${granularityLabel(granularity).toLowerCase()} buckets`}
      action={legend.length > 1 ? <SeriesLegend items={legend} /> : null}
      meta={data.length > 0 ? <StatStrip stats={stats} currency={baseCurrency} countLabel="Periods" /> : null}
      chartHeight={320}
    >
      {data.length === 0 ? (
        <EmptyState title={`Nothing for ${category.name} in this period`} hint="Pick a wider period or another category." />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke={ink.grid} />
            <XAxis
              dataKey="start"
              tickFormatter={(start) => formatTrendBucket(String(start), granularity)}
              tick={{ fill: ink.textSecondary, fontSize: 12 }}
              axisLine={false}
              tickLine={false}
              interval="equidistantPreserveStart"
              minTickGap={32}
            />
            <YAxis
              tickFormatter={formatCompact}
              tick={{ fill: ink.textSecondary, fontSize: 12 }}
              axisLine={false}
              tickLine={false}
              width={46}
            />
            <Tooltip content={<TrendTooltip baseCurrency={baseCurrency} />} cursor={{ stroke: ink.textSecondary, strokeDasharray: '3 3' }} />
            {hasExpense ? (
              <Line type="monotone" dataKey="expense" name="Expense" stroke={colors.expense} strokeWidth={2} dot={{ r: 2.5 }} isAnimationActive={false} />
            ) : null}
            {hasIncome ? (
              <Line type="monotone" dataKey="income" name="Income" stroke={colors.income} strokeWidth={2} dot={{ r: 2.5 }} isAnimationActive={false} />
            ) : null}
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  )
}

interface TrendTooltipProps {
  active?: boolean
  payload?: { name?: string; color?: string; value?: number | string; payload?: StatisticsTrendPoint }[]
  baseCurrency: string
}

function TrendTooltip({ active, payload, baseCurrency }: TrendTooltipProps) {
  if (!active || !payload?.length) return null
  const point = payload[0]?.payload
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
