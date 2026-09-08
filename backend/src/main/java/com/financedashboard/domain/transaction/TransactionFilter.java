package com.financedashboard.domain.transaction;

import java.time.LocalDate;

/**
 * Filters for listing transactions. A null field means "no constraint"; {@code uncategorized}
 * selects transactions without a category.
 */
public record TransactionFilter(
        Long accountId,
        Long categoryId,
        Boolean uncategorized,
        TransactionNature nature,
        LocalDate from,
        LocalDate to,
        String query) {
}
