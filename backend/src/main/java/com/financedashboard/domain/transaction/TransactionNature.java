package com.financedashboard.domain.transaction;

import java.math.BigDecimal;

/**
 * Whether a transaction is income, an expense, or an internal transfer between the user's own
 * accounts. Only {@code INCOME} and {@code EXPENSE} participate in statistics.
 */
public enum TransactionNature {
    INCOME,
    EXPENSE,
    TRANSFER;

    /** Returns {@code EXPENSE} for a negative amount, otherwise {@code INCOME}. */
    public static TransactionNature forSignedAmount(BigDecimal amount) {
        return amount.signum() < 0 ? EXPENSE : INCOME;
    }
}
