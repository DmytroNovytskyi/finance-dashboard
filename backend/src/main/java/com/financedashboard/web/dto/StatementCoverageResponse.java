package com.financedashboard.web.dto;

import com.financedashboard.domain.statement.StatementCoverage;
import java.time.LocalDate;
import java.util.List;

/** API representation of how far one account's imported statements reach. */
public record StatementCoverageResponse(
        Long accountId,
        LocalDate earliestPeriodStart,
        LocalDate latestPeriodEnd,
        int statementCount,
        List<PeriodGapResponse> gaps,
        List<LocalDate> missingPeriodEnds) {

    /** A stretch of days no statement covers, inclusive of both bounds. */
    public record PeriodGapResponse(LocalDate from, LocalDate to) {
    }

    public static StatementCoverageResponse from(StatementCoverage coverage) {
        return new StatementCoverageResponse(
                coverage.accountId(),
                coverage.earliestPeriodStart(),
                coverage.latestPeriodEnd(),
                coverage.statementCount(),
                coverage.gaps().stream()
                        .map(gap -> new PeriodGapResponse(gap.from(), gap.to()))
                        .toList(),
                coverage.missingPeriodEnds());
    }
}
