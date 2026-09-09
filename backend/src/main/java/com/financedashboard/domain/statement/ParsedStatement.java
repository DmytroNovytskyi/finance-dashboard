package com.financedashboard.domain.statement;

import java.time.LocalDate;
import java.util.List;

/**
 * The outcome of parsing one statement document. {@code accountNumber} is the account the
 * statement belongs to, when the document names it (canonicalized digits-only form, may be null).
 */
public record ParsedStatement(
        String bank,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ParsedTransaction> transactions,
        String accountNumber) {

    /** Convenience constructor for statements that do not name their account. */
    public ParsedStatement(
            String bank,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<ParsedTransaction> transactions) {
        this(bank, currency, periodStart, periodEnd, transactions, null);
    }
}
