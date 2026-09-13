import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Grid from '@mui/material/Grid'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { queryKeys } from '../../api/keys'
import { statisticsApi } from '../../api/endpoints'
import { buildQuery } from '../../api/client'
import type { DateRange } from '../../lib/date'
import { amountColor, useScheme } from '../../theme'
import { formatInteger, formatMoney, formatMoneyMagnitude, formatTrendBucket } from '../../lib/format'
import { ChartCard } from '../../components/ChartCard'
import { PageHeader, PageScroll, PageShell } from '../../components/PageLayout'
import { SeriesLegend } from '../../components/SeriesLegend'
import { StatementFreshness } from '../../components/StatementFreshness'
import type { StatisticsGranularity, StatisticsTrendPoint, TransactionNature } from '../../types'
import { CategoryDonut, type DonutRow } from './CategoryDonut'
import { MetricToggle, type Direction } from './MetricToggle'
import { useDisplayCurrency, type DisplayCurrency } from '../preferences/displayCurrency'
import {
  useStatisticsPreferences,
  type PeriodSelection,
  type TrendGranularity,
} from '../preferences/statisticsPreferences'
import { PeriodSelector } from './PeriodSelector'
import { TopMerchantsChart } from './TopMerchantsChart'
import { TrendChart } from './TrendChart'

const OVERVIEW_STORAGE_KEY = 'finance-dashboard.overview.v1'

/** The label the statistics give the rows they could not attribute to any merchant. */
const NO_MERCHANT = '(no merchant)'

const DEFAULT_DIRECTION: Direction = 'expense'

/**
 * How the donut and the merchant chart share their row: side by side from `lg` up, stacked below
 * it. The `nowrap` is load-bearing rather than cosmetic. A **wrapping** flex line is sized to its
 * tallest item's content, so under `wrap` the category list sets the row's height instead of being
 * bounded by it — a dozen categories made the card 486px tall inside a 383px row, and the 103px it
 * spilled pushed a scrollbar onto the page. With `nowrap` the row's height is definite, the card
 * takes it, and the list scrolls inside the card the way its own `overflow` always intended.
 */
const CHART_ROW_WRAP = { xs: 'wrap', lg: 'nowrap' } as const

interface StoredOverviewState {
  categoryMetric?: unknown
  merchantMetric?: unknown
}

/**
 * Restores which side each card of the breakdown row is showing. The period and the granularity are
 * not stored here: both statistics pages share them through {@link useStatisticsPreferences}.
 */
function loadOverviewState(): { categoryMetric: Direction; merchantMetric: Direction } {
  const fallback = { categoryMetric: DEFAULT_DIRECTION, merchantMetric: DEFAULT_DIRECTION }
  try {
    const raw = window.localStorage.getItem(OVERVIEW_STORAGE_KEY)
    if (!raw) return fallback
    const stored = JSON.parse(raw) as StoredOverviewState
    const read = (value: unknown): Direction =>
      value === 'expense' || value === 'income' ? value : DEFAULT_DIRECTION
    return { categoryMetric: read(stored.categoryMetric), merchantMetric: read(stored.merchantMetric) }
  } catch {
    return fallback
  }
}

interface KpiTileProps {
  label: string
  value: string
  color?: string
  onClick?: () => void
}

function KpiTile({ label, value, color, onClick }: KpiTileProps) {
  return (
    <Paper
      variant="outlined"
      component={onClick ? 'button' : 'div'}
      onClick={onClick}
      sx={{
        p: 2,
        width: '100%',
        height: '100%',
        minHeight: 84,
        textAlign: 'left',
        borderRadius: 3,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        gap: 0.25,
        cursor: onClick ? 'pointer' : 'default',
        borderColor: 'divider',
        '&:hover': onClick ? { borderColor: 'text.primary', transition: 'border-color 120ms' } : {},
      }}
    >
      <Typography variant="caption" color="text.secondary" sx={{ textTransform: 'uppercase', letterSpacing: 0.05 }}>
        {label}
      </Typography>
      <Typography variant="h6" sx={{ fontWeight: 600, color, lineHeight: 1.2 }}>
        {value}
      </Typography>
    </Paper>
  )
}

function rangeParams(range: DateRange, granularity?: StatisticsGranularity, displayCurrency?: DisplayCurrency) {
  return {
    from: range.from ?? undefined,
    to: range.to ?? undefined,
    granularity,
    displayCurrency,
  }
}

/** Dashboard: KPI tiles plus the bucketed trend, category donut, and top merchants. */
export function OverviewPage() {
  const scheme = useScheme()
  const navigate = useNavigate()
  const { displayCurrency } = useDisplayCurrency()
  const { period, granularity, setPeriod, setGranularity } = useStatisticsPreferences()
  const [categoryMetric, setCategoryMetric] = useState<Direction>(() => loadOverviewState().categoryMetric)
  const [merchantMetric, setMerchantMetric] = useState<Direction>(() => loadOverviewState().merchantMetric)
  const [focus, setFocus] = useState<DateRange | null>(null)

  const changePeriod = (next: PeriodSelection) => {
    setPeriod(next)
    setFocus(null)
  }
  const changeGranularity = (next: TrendGranularity) => {
    setGranularity(next)
    setFocus(null)
  }

  useEffect(() => {
    try {
      window.localStorage.setItem(
        OVERVIEW_STORAGE_KEY,
        JSON.stringify({ categoryMetric, merchantMetric }),
      )
    } catch {
      /* storage unavailable: keep the selection for this session only */
    }
  }, [categoryMetric, merchantMetric])

  /**
   * The bucket the trend chart is drawn at. "Transaction" means one point per transaction, which
   * only a per-category series chart can draw, so the trend falls back to months and the card says
   * so rather than letting the control claim a granularity the chart is not using.
   */
  const trendGranularity: StatisticsGranularity = granularity === 'transaction' ? 'month' : granularity

  const trendQuery = useQuery({
    queryKey: queryKeys.statistics.summary(rangeParams(period.range, trendGranularity, displayCurrency)),
    queryFn: () => statisticsApi.summary(rangeParams(period.range, trendGranularity, displayCurrency)),
    placeholderData: keepPreviousData,
  })
  const overviewRange = focus ?? period.range
  const overviewQuery = useQuery({
    queryKey: queryKeys.statistics.summary(rangeParams(overviewRange, undefined, displayCurrency)),
    queryFn: () => statisticsApi.summary(rangeParams(overviewRange, undefined, displayCurrency)),
    placeholderData: keepPreviousData,
  })

  const baseCurrency = overviewQuery.data?.baseCurrency ?? trendQuery.data?.baseCurrency ?? 'PLN'
  const totals = overviewQuery.data?.totals
  const colors = amountColor[scheme]
  const loading = trendQuery.isLoading || (overviewQuery.isLoading && !overviewQuery.data)

  const openTransactions = (filters: {
    categoryId?: number
    uncategorized?: boolean
    nature?: TransactionNature
    merchant?: string
    withoutMerchant?: boolean
  }) => {
    const query = buildQuery({
      from: overviewRange.from ?? undefined,
      to: overviewRange.to ?? undefined,
      ...filters,
    })
    navigate(`/transactions${query}`)
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 320 }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <PageShell>
      <PageHeader title="Overview" subtitle="Where the money comes from and where it goes." />

      <Box sx={{ minWidth: 320 }}>
        <PeriodSelector
          period={period}
          granularity={granularity}
          onChange={changePeriod}
          onGranularityChange={changeGranularity}
        />
      </Box>

      {overviewQuery.isError ? (
        <Alert severity="error">Could not load the statistics. Check that the backend is running.</Alert>
      ) : null}

      <StatementFreshness />

      <PageScroll>
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
            flex: 1,
            minHeight: 0,
            opacity: trendQuery.isPlaceholderData || overviewQuery.isPlaceholderData ? 0.5 : 1,
            transition: 'opacity 150ms',
            pointerEvents: trendQuery.isPlaceholderData || overviewQuery.isPlaceholderData ? 'none' : 'auto',
          }}
        >
        {focus ? (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', flexShrink: 0 }}>
            <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75 }}>
              <Typography variant="body2" color="text.secondary">
                Showing
              </Typography>
              <Chip
                label={formatTrendBucket(focus.from ?? '', trendGranularity)}
                onDelete={() => setFocus(null)}
                size="small"
                variant="outlined"
              />
              <Typography variant="caption" color="text.secondary">
                in the donut &amp; merchants; the trend keeps the full period.
              </Typography>
            </Box>
          </Box>
        ) : null}

        <Grid container spacing={2}>
          {totals ? (
            <>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Spend" value={formatMoneyMagnitude(totals.expense, baseCurrency)} color={colors.expense} onClick={() => openTransactions({ nature: 'EXPENSE' })} />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Income" value={formatMoneyMagnitude(totals.income, baseCurrency)} color={colors.income} onClick={() => openTransactions({ nature: 'INCOME' })} />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Net" value={formatMoney(totals.net, baseCurrency)} color={totals.net >= 0 ? colors.income : colors.expense} />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Avg / day" value={formatMoneyMagnitude(totals.avgExpensePerDay, baseCurrency)} />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Transactions" value={formatInteger(totals.count)} />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Uncategorized" value={formatInteger(totals.uncategorizedCount)} onClick={() => navigate('/categorize')} />
              </Grid>
            </>
          ) : (
            <Grid size={12}>
              <Typography color="text.secondary">No data for this period.</Typography>
            </Grid>
          )}
        </Grid>

        <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 0', minHeight: 140 }}>
          <ChartCard
            title="Income and expenses"
            subtitle={
              granularity === 'transaction'
                ? 'Individual transactions are not drawn here — showing monthly buckets.'
                : undefined
            }
            action={
              <SeriesLegend
                items={[
                  { label: 'Income', color: colors.income },
                  { label: 'Expense', color: colors.expense },
                ]}
              />
            }
            chartHeight={90}
          >
            <TrendChart
              data={trendQuery.data?.trend ?? []}
              baseCurrency={baseCurrency}
              granularity={trendGranularity}
              onSelect={(point: StatisticsTrendPoint) => setFocus({ from: point.start, to: point.end })}
            />
          </ChartCard>
        </Box>

        <Box sx={{ display: 'flex', gap: 2, flexWrap: CHART_ROW_WRAP, flex: '1 1 0', minHeight: 140 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 320px', minWidth: 0, minHeight: 0 }}>
            <ChartCard
              title={categoryMetric === 'expense' ? 'Spend by category' : 'Income by category'}
              subtitle="Click a slice to see the transactions"
              action={
                <MetricToggle
                  direction={categoryMetric}
                  onChange={setCategoryMetric}
                  label="Category direction"
                />
              }
              chartHeight={90}
            >
              <CategoryDonut
                byCategory={overviewQuery.data?.byCategory ?? []}
                baseCurrency={baseCurrency}
                direction={categoryMetric}
                onSelect={(row: DonutRow) => openTransactions(row.categoryId === null ? { uncategorized: true } : { categoryId: row.categoryId })}
              />
            </ChartCard>
          </Box>
          <Box sx={{ display: 'flex', flexDirection: 'column', flex: '1 1 320px', minWidth: 0, minHeight: 0 }}>
            <ChartCard
              title={merchantMetric === 'expense' ? 'Spend by merchant' : 'Income by merchant'}
              subtitle="Click a bar to see the transactions"
              action={
                <MetricToggle
                  direction={merchantMetric}
                  onChange={setMerchantMetric}
                  label="Merchant direction"
                />
              }
              chartHeight={90}
            >
              <TopMerchantsChart
                topMerchants={overviewQuery.data?.topMerchants ?? []}
                baseCurrency={baseCurrency}
                direction={merchantMetric}
                onSelect={(merchant) =>
                  openTransactions(merchant === NO_MERCHANT ? { withoutMerchant: true } : { merchant })
                }
              />
            </ChartCard>
          </Box>
        </Box>
        </Box>
      </PageScroll>
    </PageShell>
  )
}
