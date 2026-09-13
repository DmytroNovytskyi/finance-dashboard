import InfoOutlined from '@mui/icons-material/InfoOutlined'
import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import { useAccountsById, useStatementCoverage } from '../api/queries'
import { formatDate, formatDateRange, formatInteger } from '../lib/format'
import type { StatementCoverage } from '../types'

/** Whether a coverage entry has anything worth flagging to the reader. */
function isStale(coverage: StatementCoverage): boolean {
  return coverage.gaps.length > 0 || coverage.missingPeriodEnds.length > 0
}

/** The period an account's statements span, or a note that there are none. */
function coverageSummary(coverage: StatementCoverage): string {
  if (coverage.earliestPeriodStart === null || coverage.latestPeriodEnd === null) {
    return 'No statements imported yet.'
  }
  return formatDateRange(coverage.earliestPeriodStart, coverage.latestPeriodEnd)
}

/** Describes what is missing for an account, or null when its coverage is complete. */
function coverageProblem(coverage: StatementCoverage): string | null {
  const problems: string[] = []
  if (coverage.gaps.length > 0) {
    const ranges = coverage.gaps.map((gap) => formatDateRange(gap.from, gap.to)).join(', ')
    const noun = coverage.gaps.length === 1 ? 'statement' : 'statements'
    problems.push(`${noun} not uploaded for ${ranges}`)
  }
  if (coverage.missingPeriodEnds.length > 0) {
    const count = coverage.missingPeriodEnds.length
    const latest = coverage.missingPeriodEnds[count - 1]
    problems.push(
      count === 1
        ? `a statement covering up to ${formatDate(latest)} has not been uploaded`
        : `${formatInteger(count)} periods not uploaded, the latest ending ${formatDate(latest)}`,
    )
  }
  return problems.length > 0 ? problems.join('; ') : null
}

/**
 * Per-account statement freshness: how far the imported statements reach and where they are
 * incomplete. Shared by the pages that read the data, so staleness is visible while using it.
 */
export function StatementFreshness() {
  const coverage = useStatementCoverage()
  const accountsById = useAccountsById()
  const rows = coverage.data ?? []
  const stale = rows.filter(isStale)

  if (coverage.isLoading || rows.length === 0) {
    return null
  }

  return (
    <Paper variant="outlined" sx={{ p: 2, borderRadius: 3, display: 'flex', flexDirection: 'column', gap: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
          Statement freshness
        </Typography>
        <Tooltip
          title="How far each account's imported statements reach."
          placement="right"
          slotProps={{ popper: { modifiers: [{ name: 'offset', options: { offset: [0, -6] } }] } }}
        >
          <InfoOutlined fontSize="small" sx={{ color: 'text.secondary', flexShrink: 0 }} />
        </Tooltip>
      </Box>

      {stale.length > 0 ? (
        <Alert severity="warning">
          {stale.length === 1
            ? 'One account has incomplete statement coverage.'
            : `${formatInteger(stale.length)} accounts have incomplete statement coverage.`}{' '}
          You may want to upload the missing statements.
        </Alert>
      ) : null}

      <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: 4, rowGap: 1 }}>
        {rows.map((row) => {
          const problem = coverageProblem(row)
          return (
            <Box key={row.accountId} sx={{ minWidth: 200 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }} noWrap>
                {accountsById.get(row.accountId)?.name ?? `Account ${row.accountId}`}
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                {coverageSummary(row)}
              </Typography>
              {problem ? (
                <Typography variant="caption" color="warning.main" sx={{ display: 'block' }}>
                  {problem}
                </Typography>
              ) : null}
            </Box>
          )
        })}
      </Box>
    </Paper>
  )
}
