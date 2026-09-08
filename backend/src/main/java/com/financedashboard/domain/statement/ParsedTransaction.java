package com.financedashboard.domain.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single transaction row parsed from a bank statement. The amount is signed: an expense is
 * negative, income positive.
 */
public record ParsedTransaction(
        LocalDate date,
        BigDecimal amount,
        String description,
        String merchant) {
}
