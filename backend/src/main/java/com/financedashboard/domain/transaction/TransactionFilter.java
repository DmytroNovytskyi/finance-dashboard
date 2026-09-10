package com.financedashboard.domain.transaction;

import java.time.LocalDate;
import java.util.List;

/**
 * Filters for listing transactions. A null field means "no constraint"; {@code uncategorized}
 * selects transactions without a category. {@code ids} restricts the list to an explicit set of
 * rows, which is how a suggestion drills through to the transactions it is talking about; a null or
 * empty list means no constraint.
 */
public record TransactionFilter(
        Long accountId,
        Long categoryId,
        Boolean uncategorized,
        TransactionNature nature,
        LocalDate from,
        LocalDate to,
        String query,
        List<Long> ids) {
}
