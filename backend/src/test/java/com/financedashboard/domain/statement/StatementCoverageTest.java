package com.financedashboard.domain.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementCoverageTest {

    private static BankStatement period(LocalDate start, LocalDate end) {
        return BankStatement.builder()
                .accountId(1L)
                .bank("PEKAO")
                .periodStart(start)
                .periodEnd(end)
                .fileName("wyciag.pdf")
                .fileHash("hash-" + start)
                .importedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
    }

    private static BankStatement undated() {
        return BankStatement.builder()
                .accountId(1L)
                .bank("PEKAO")
                .fileName("unknown.pdf")
                .fileHash("hash-undated")
                .importedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
    }

    @Test
    void reportsTheOuterBoundsAndNoGapsWhenPeriodsAreContiguous() {
        StatementCoverage coverage = StatementCoverage.of(1L, List.of(
                period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                period(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))),
                LocalDate.of(2026, 3, 1));

        assertThat(coverage.earliestPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(coverage.latestPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(coverage.statementCount()).isEqualTo(2);
        assertThat(coverage.gaps()).isEmpty();
        assertThat(coverage.missingPeriodEnds()).isEmpty();
    }

    @Test
    void ordersBoundsByPeriodRatherThanByTheOrderStatementsWereSupplied() {
        StatementCoverage coverage = StatementCoverage.of(1L, List.of(
                period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)),
                period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))),
                LocalDate.of(2026, 4, 1));

        assertThat(coverage.earliestPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(coverage.latestPeriodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void reportsTheHoleLeftByASkippedStatement() {
        StatementCoverage coverage = StatementCoverage.of(1L, List.of(
                period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))),
                LocalDate.of(2026, 4, 1));

        assertThat(coverage.gaps()).containsExactly(
                new StatementCoverage.PeriodGap(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        assertThat(coverage.missingPeriodEnds()).isEmpty();
    }

    @Test
    void reportsEveryHoleWhenSeveralStatementsAreMissing() {
        StatementCoverage coverage = StatementCoverage.of(1L, List.of(
                period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
                period(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))),
                LocalDate.of(2026, 7, 1));

        assertThat(coverage.gaps()).containsExactly(
                new StatementCoverage.PeriodGap(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31)),
                new StatementCoverage.PeriodGap(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));
    }

    @Test
    void doesNotReportTheCurrentPeriodBeforeItHasClosed() {
        StatementCoverage coverage = StatementCoverage.of(1L,
                List.of(period(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 25))),
                LocalDate.of(2026, 9, 10));

        assertThat(coverage.missingPeriodEnds()).isEmpty();
    }

    @Test
    void reportsAPeriodAsMissingOnceTheDayAfterItClosedHasPassed() {
        StatementCoverage coverage = StatementCoverage.of(1L,
                List.of(period(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 25))),
                LocalDate.of(2026, 9, 26));

        assertThat(coverage.missingPeriodEnds()).containsExactly(LocalDate.of(2026, 9, 25));
    }

    @Test
    void keepsAMonthEndCycleOnTheMonthEndRatherThanDrifting() {
        StatementCoverage coverage = StatementCoverage.of(1L,
                List.of(period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))),
                LocalDate.of(2026, 4, 1));

        assertThat(coverage.missingPeriodEnds()).containsExactly(
                LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }

    @Test
    void capsTheMissingPeriodsOfALongDormantAccount() {
        StatementCoverage coverage = StatementCoverage.of(1L,
                List.of(period(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31))),
                LocalDate.of(2030, 1, 1));

        assertThat(coverage.missingPeriodEnds()).hasSize(24);
        assertThat(coverage.missingPeriodEnds()).startsWith(LocalDate.of(2020, 2, 29));
    }

    @Test
    void countsStatementsWithoutPeriodsButTakesNoDatesFromThem() {
        StatementCoverage coverage = StatementCoverage.of(1L, List.of(
                undated(),
                period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))),
                LocalDate.of(2026, 2, 1));

        assertThat(coverage.statementCount()).isEqualTo(2);
        assertThat(coverage.earliestPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(coverage.latestPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void reportsNoCoverageForAnAccountWithoutAnyDatedStatement() {
        StatementCoverage coverage = StatementCoverage.of(7L, List.of(), LocalDate.of(2026, 9, 10));

        assertThat(coverage.accountId()).isEqualTo(7L);
        assertThat(coverage.statementCount()).isZero();
        assertThat(coverage.earliestPeriodStart()).isNull();
        assertThat(coverage.latestPeriodEnd()).isNull();
        assertThat(coverage.gaps()).isEmpty();
        assertThat(coverage.missingPeriodEnds()).isEmpty();
    }
}
