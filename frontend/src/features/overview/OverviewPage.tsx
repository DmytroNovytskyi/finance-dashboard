import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
import Grid from '@mui/material/Grid'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import { queryKeys } from '../../api/keys'
import { statisticsApi } from '../../api/endpoints'
import { buildQuery } from '../../api/client'
import { rangeForPreset, type DateRange, type DateRangePreset } from '../../lib/date'
import { amountColor, useScheme } from '../../theme'
import { formatInteger, formatMoney, formatMoneyMagnitude } from '../../lib/format'
import { ChartCard } from '../../components/ChartCard'
import { CategoryDonut, type DonutRow } from './CategoryDonut'
import { MonthlyTrendChart, TrendLegend } from './MonthlyTrendChart'
import { PeriodSelector } from './PeriodSelector'
import { TopMerchantsChart } from './TopMerchantsChart'

interface PeriodState {
  preset: DateRangePreset
  range: DateRange
}

interface KpiTileProps {
  label: string
  value: string
  color?: string
  sub?: string
  onClick?: () => void
}

function KpiTile({ label, value, color, sub, onClick }: KpiTileProps) {
  return (
    <Paper
      variant="outlined"
      component={onClick ? 'button' : 'div'}
      onClick={onClick}
      sx={{
        p: 2,
        width: '100%',
        textAlign: 'left',
        borderRadius: 3,
        display: 'flex',
        flexDirection: 'column',
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
      {sub ? (
        <Typography variant="caption" color="text.disabled">
          {sub}
        </Typography>
      ) : null}
    </Paper>
  )
}

/** Dashboard: KPI tiles plus spend-by-month, by-category, and by-merchant charts. */
export function OverviewPage() {
  const scheme = useScheme()
  const navigate = useNavigate()
  const [period, setPeriod] = useState<PeriodState>({ preset: 'thisYear', range: rangeForPreset('thisYear') })

  const changePeriod = (preset: DateRangePreset, range: DateRange) => setPeriod({ preset, range })

  const params = { from: period.range.from ?? undefined, to: period.range.to ?? undefined }
  const summaryQuery = useQuery({
    queryKey: queryKeys.statistics.summary(params),
    queryFn: () => statisticsApi.summary(params),
    placeholderData: keepPreviousData,
  })

  const baseCurrency = summaryQuery.data?.baseCurrency ?? 'PLN'
  const totals = summaryQuery.data?.totals
  const colors = amountColor[scheme]

  const openTransactions = (filters: { categoryId?: number; uncategorized?: boolean }) => {
    const query = buildQuery({
      from: period.range.from ?? undefined,
      to: period.range.to ?? undefined,
      ...filters,
    })
    navigate(`/transactions${query}`)
  }

  if (summaryQuery.isLoading) {
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

      {summaryQuery.isError ? (
        <Alert severity="error">Could not load the statistics. Check that the backend is running.</Alert>
      ) : null}

      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          opacity: summaryQuery.isPlaceholderData ? 0.5 : 1,
          transition: 'opacity 150ms',
          pointerEvents: summaryQuery.isPlaceholderData ? 'none' : 'auto',
        }}
      >
        <Grid container spacing={2}>
          {totals ? (
            <>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile
                  label="Spend"
                  value={formatMoneyMagnitude(totals.expense, baseCurrency)}
                  color={colors.expense}
                  sub={`of ${formatMoneyMagnitude(totals.income, baseCurrency)} income`}
                  onClick={() => openTransactions({})}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile
                  label="Income"
                  value={formatMoneyMagnitude(totals.income, baseCurrency)}
                  color={colors.income}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile
                  label="Net"
                  value={formatMoney(totals.net, baseCurrency)}
                  color={totals.net >= 0 ? colors.income : colors.expense}
                  sub="income minus expenses"
                />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile
                  label="Avg / day"
                  value={formatMoneyMagnitude(totals.avgExpensePerDay, baseCurrency)}
                  sub={`${formatInteger(totals.count)} transactions`}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile
                  label="Uncategorized"
                  value={formatInteger(totals.uncategorizedCount)}
                  color={totals.uncategorizedCount > 0 ? '#fab219' : colors.income}
                  sub="rows to tag"
                  onClick={() => navigate('/categorize')}
                />
              </Grid>
              <Grid size={{ xs: 6, md: 4, lg: 2 }}>
                <KpiTile
                  label="Unconverted"
                  value={formatInteger(summaryQuery.data?.unconverted ?? 0)}
                  sub="no FX rate available"
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
            <ChartCard title="Income and expenses by month" action={<TrendLegend />} chartHeight={300}>
              <MonthlyTrendChart data={summaryQuery.data?.byMonth ?? []} baseCurrency={baseCurrency} />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ChartCard title="Spend by category" subtitle="Click a slice to see the transactions" chartHeight={300}>
              <CategoryDonut byCategory={summaryQuery.data?.byCategory ?? []} baseCurrency={baseCurrency} onSelect={(row: DonutRow) => openTransactions(row.categoryId === null ? { uncategorized: true } : { categoryId: row.categoryId })} />
            </ChartCard>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <ChartCard title="Top merchants" chartHeight={300}>
              <TopMerchantsChart topMerchants={summaryQuery.data?.topMerchants ?? []} baseCurrency={baseCurrency} />
            </ChartCard>
          </Grid>
        </Grid>
      </Box>
    </Box>
  )
}
