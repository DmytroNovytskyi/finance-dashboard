package com.financedashboard.domain.port;

import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import java.util.Optional;

/** Outbound port for storing and reading {@link Transaction} aggregates. */
public interface TransactionRepository {

    /** Saves the transaction and returns the stored state (with its generated id). */
    Transaction save(Transaction transaction);

    /** Returns the transaction with the given id, if present. */
    Optional<Transaction> findById(Long id);

    /**
     * Returns one page of transactions matching {@code filter}, ordered by transaction date
     * descending then id descending.
     */
    PagedTransactions search(TransactionFilter filter, int page, int size);
}
