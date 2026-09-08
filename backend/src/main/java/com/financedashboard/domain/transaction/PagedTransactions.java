package com.financedashboard.domain.transaction;

import java.util.List;

/** A page of transaction search results with the total element count. */
public record PagedTransactions(
        List<Transaction> content,
        long totalElements,
        int page,
        int size) {

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
