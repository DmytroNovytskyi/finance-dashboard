package com.financedashboard.domain.statement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How far one account's imported statements reach, and where they are incomplete: the earliest and
 * latest period covered, any hole between consecutive statements, and any period that has already
 * closed without a statement being imported.
 *
 * <p>Periods are compared by contiguity, not by calendar month. Accounts close on different cycles
 * — some on a fixed day of the month, others at month end — so a calendar-month rule would report
 * false gaps. A reported gap or missing period is therefore <em>possibly</em> absent data: a month
 * with no activity may legitimately produce no statement.
 */
public record StatementCoverage(
        Long accountId,
        LocalDate earliestPeriodStart,
        LocalDate latestPeriodEnd,
        int statementCount,
        List<PeriodGap> gaps,
        List<LocalDate> missingPeriodEnds) {

    private static final int MAX_MISSING_PERIODS = 24;

    /** A stretch of days no statement covers, inclusive of both bounds. */
    public record PeriodGap(LocalDate from, LocalDate to) {
    }

    /**
     * Derives the coverage of one account from its statements as of {@code today}. Every supplied
     * statement is counted, but only those carrying both period bounds contribute dates, since a
     * statement without them cannot be placed on a timeline.
     */
    public static StatementCoverage of(Long accountId, List<BankStatement> statements, LocalDate today) {
        List<BankStatement> dated = statements.stream()
                .filter(statement -> statement.getPeriodStart() != null && statement.getPeriodEnd() != null)
                .sorted(Comparator.comparing(BankStatement::getPeriodStart))
                .toList();
        if (dated.isEmpty()) {
            return new StatementCoverage(accountId, null, null, statements.size(), List.of(), List.of());
        }
        LocalDate latestEnd = dated.stream()
                .map(BankStatement::getPeriodEnd)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return new StatementCoverage(accountId, dated.get(0).getPeriodStart(), latestEnd,
                statements.size(), gapsBetween(dated), missingAfter(dated, latestEnd, today));
    }

    /** Finds holes where the next statement starts later than the day after the previous one ends. */
    private static List<PeriodGap> gapsBetween(List<BankStatement> dated) {
        List<PeriodGap> gaps = new ArrayList<>();
        for (int i = 1; i < dated.size(); i++) {
            LocalDate previousEnd = dated.get(i - 1).getPeriodEnd();
            LocalDate nextStart = dated.get(i).getPeriodStart();
            if (nextStart.isAfter(previousEnd.plusDays(1))) {
                gaps.add(new PeriodGap(previousEnd.plusDays(1), nextStart.minusDays(1)));
            }
        }
        return List.copyOf(gaps);
    }

    /**
     * Collects each period end that has already passed without a statement, by measuring whole
     * months from the latest covered period. Measuring from the original date rather than from the
     * previous candidate matters: adding a month at a time would drift a month-end cycle backwards
     * (31 Jan → 28 Feb → 28 Mar), whereas whole months keep it on the month end (31 Mar).
     */
    private static List<LocalDate> missingAfter(List<BankStatement> dated, LocalDate latestEnd, LocalDate today) {
        Set<LocalDate> coveredEnds = dated.stream()
                .map(BankStatement::getPeriodEnd)
                .collect(Collectors.toSet());
        List<LocalDate> missing = new ArrayList<>();
        for (int months = 1; missing.size() < MAX_MISSING_PERIODS; months++) {
            LocalDate candidate = latestEnd.plusMonths(months);
            if (!today.isAfter(candidate)) {
                break;
            }
            if (!coveredEnds.contains(candidate)) {
                missing.add(candidate);
            }
        }
        return List.copyOf(missing);
    }
}
