package com.financedashboard.domain.transaction;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Whether a transaction is income, an expense, one leg of an internal transfer between the user's
 * own accounts, or one leg of a refund. Only {@code INCOME} and {@code EXPENSE} participate in
 * statistics: the two legs of a transfer and of a refund cancel each other out and are excluded.
 *
 * <p>Both legs of a refund carry {@code REFUND}, the purchase as well as the money coming back —
 * the purchase is not spending because it was reversed.
 */
public enum TransactionNature {
    INCOME,
    EXPENSE,
    TRANSFER,
    REFUND;

    /**
     * Returns {@code EXPENSE} for a negative amount, otherwise {@code INCOME}. This is the natural
     * nature, so un-pairing a transfer or a refund restores what the row would have been.
     */
    public static TransactionNature forSignedAmount(BigDecimal amount) {
        return amount.signum() < 0 ? EXPENSE : INCOME;
    }

    /** The natures that cancel themselves out and so take no part in any statistic. */
    private static final Set<TransactionNature> EXCLUDED_FROM_STATISTICS = Set.of(TRANSFER, REFUND);

    /** Returns every nature that statistics leave out. */
    public static Set<TransactionNature> excludedFromStatistics() {
        return EXCLUDED_FROM_STATISTICS;
    }

    /** Whether this nature cancels itself out and so takes no part in any statistic. */
    public boolean isExcludedFromStatistics() {
        return EXCLUDED_FROM_STATISTICS.contains(this);
    }
}
