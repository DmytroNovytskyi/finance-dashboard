package com.financedashboard.domain.statement;

import java.time.LocalDate;
import java.util.List;

/** The outcome of parsing one statement document. */
public record ParsedStatement(
        String bank,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ParsedTransaction> transactions) {
}
