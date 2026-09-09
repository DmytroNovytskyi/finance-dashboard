import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Grid from '@mui/material/Grid'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import Paper from '@mui/material/Paper'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { queryKeys } from '../../api/keys'
import { statisticsApi } from '../../api/endpoints'
import { buildQuery } from '../../api/client'
import { rangeForPreset, type DateRange, type DateRangePreset } from '../../lib/date'
import { amountColor, useScheme } from '../../theme'
import { formatInteger, formatMoney, formatMoneyMagnitude, formatTrendBucket } from '../../lib/format'
import { ChartCard } from '../../components/ChartCard'
import type { StatisticsGranularity, StatisticsTrendPoint } from '../../types'
import { CategoryDonut, type DonutRow } from './CategoryDonut'
import { PeriodSelector } from './PeriodSelector'
import { TopMerchantsChart } from './TopMerchantsChart'
import { TrendChart, TrendControls } from './TrendChart'

interface PeriodState {
  preset: DateRangePreset
  range: DateRange
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

function rangeParams(range: DateRange, granularity?: StatisticsGranularity) {
  return { from: range.from ?? undefined, to: range.to ?? undefined, granularity }
}

/** Dashboard: KPI tiles plus the bucketed trend, category donut, and top merchants. */
export function OverviewPage() {
  const scheme = useScheme()
  const navigate = useNavigate()
  const [period, setPeriod] = useState<PeriodState>({ preset: 'thisYear', range: rangeForPreset('thisYear') })
  const [granularity, setGranularity] = useState<StatisticsGranularity>('month')
  const [focus, setFocus] = useState<DateRange | null>(null)

  const changePeriod = (preset: DateRangePreset, range: DateRange) => {
    setPeriod({ preset, range })
    setFocus(null)
  }
  const changeGranularity = (next: StatisticsGranularity) => {
    setGranularity(next)
    setFocus(null)
  }

  const trendQuery = useQuery({
    queryKey: queryKeys.statistics.summary(rangeParams(period.range, granularity)),
    queryFn: () => statisticsApi.summary(rangeParams(period.range, granularity)),
    placeholderData: keepPreviousData,
  })
  const overviewRange = focus ?? period.range
  const overviewQuery = useQuery({
    queryKey: queryKeys.statistics.summary(rangeParams(overviewRange)),
    queryFn: () => statisticsApi.summary(rangeParams(overviewRange)),
    placeholderData: keepPreviousData,
  })

  const baseCurrency = overviewQuery.data?.baseCurrency ?? trendQuery.data?.baseCurrency ?? 'PLN'
  const totals = overviewQuery.data?.totals
  const colors = amountColor[scheme]
  const loading = trendQuery.isLoading || (overviewQuery.isLoading && !overviewQuery.data)

  const openTransactions = (filters: { categoryId?: number; uncategorized?: boolean }) => {
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
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Overview
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Where the money comes from and where it goes.
        </Typography>
      </Box>

      <PeriodSelector preset={period.preset} range={period.range} onChange={changePeriod} />

      {overviewQuery.isError ? (
        <Alert severity="error">Could not load the statistics. Check that the backend is running.</Alert>
      ) : null}

      {(overviewQuery.data?.unconverted ?? 0) > 0 ? (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Tooltip title="Rows in a foreign currency for which no NBP rate was available when imported. They are not counted in the money figures.">
            <InfoOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
          </Tooltip>
          <Typography variant="caption" color="text.secondary">
            {formatInteger(overviewQuery.data?.unconverted ?? 0)} transactions have no FX rate and aren&apos;t in the money figures.
          </Typography>
        </Box>
      ) : null}

      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          opacity: trendQuery.isPlaceholderData || overviewQuery.isPlaceholderData ? 0.5 : 1,
          transition: 'opacity 150ms',
          pointerEvents: trendQuery.isPlaceholderData || overviewQuery.isPlaceholderData ? 'none' : 'auto',
        }}
      >
        {focus ? (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Showing
            </Typography>
            <Chip
              label={formatTrendBucket(focus.from ?? '', granularity)}
              onDelete={() => setFocus(null)}
              size="small"
              variant="outlined"
            />
            <Typography variant="body2" color="text.secondary">
              in the donut and merchants; the trend keeps the full period.
            </Typography>
          </Box>
        ) : null}

        <Grid container spacing={2}>
          {totals ? (
            <>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Spend" value={formatMoneyMagnitude(totals.expense, baseCurrency)} color={colors.expense} onClick={() => openTransactions({})} />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile label="Income" value={formatMoneyMagnitude(totals.income, baseCurrency)} color={colors.income} />
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
                <KpiTile
                  label="Uncategorized"
                  value={formatInteger(totals.uncategorizedCount)}
                  color={totals.uncategorizedCount > 0 ? '#c0392b' : colors.income}
                  onClick={() => navigate('/categorize')}
                />
              </Grid>
            </>
          ) : (
            <Grid size={12}>
              <Typography color="text.secondary">No data for this period.</Typography>
            </Grid>
          )}
        </Grid>

        <Grid container spacing={2}>
          <Grid size={12}>
            <ChartCard
              title="Income and expenses"
              action={<TrendControls granularity={granularity} onGranularityChange={changeGranularity} />}
              chartHeight={300}
            >
              <TrendChart
                data={trendQuery.data?.trend ?? []}
                baseCurrency={baseCurrency}
                granularity={granularity}
                onSelect={(point: StatisticsTrendPoint) => setFocus({ from: point.start, to: point.end })}
              />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ChartCard title="Spend by category" subtitle="Click a slice to see the transactions" chartHeight={300}>
              <CategoryDonut byCategory={overviewQuery.data?.byCategory ?? []} baseCurrency={baseCurrency} onSelect={(row: DonutRow) => openTransactions(row.categoryId === null ? { uncategorized: true } : { categoryId: row.categoryId })} />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ChartCard title="Top merchants" chartHeight={300}>
              <TopMerchantsChart topMerchants={overviewQuery.data?.topMerchants ?? []} baseCurrency={baseCurrency} />
            </ChartCard>
          </Grid>
        </Grid>
      </Box>
    </Box>
  )
}
