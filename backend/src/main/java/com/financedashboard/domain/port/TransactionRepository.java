package com.financedashboard.domain.port;

import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Outbound port for storing and reading {@link Transaction} aggregates. */
public interface TransactionRepository {

    /** Saves the transaction and returns the stored state (with its generated id). */
    Transaction save(Transaction transaction);

    /** Saves all transactions and returns their stored state. */
    List<Transaction> saveAll(Collection<Transaction> transactions);

    /** Returns the dedup hashes already present for the given account among {@code hashes}. */
    Set<String> findExistingDedupHashes(Long accountId, Collection<String> hashes);

    /** Returns the transaction with the given id, if present. */
    Optional<Transaction> findById(Long id);

    /** Returns transactions whose value date is within the inclusive range, optionally for one account. */
    List<Transaction> findByDateRangeAndAccount(LocalDate from, LocalDate to, Long accountId);

    /** Returns transactions belonging to any of the given transfer groups. */
    List<Transaction> findByTransferGroupIds(Collection<UUID> transferGroupIds);

    /** Returns whether any transaction belongs to the statement with the given id. */
    boolean existsByStatementId(Long statementId);

    /** Deletes the given transactions. */
    void deleteAll(Collection<Transaction> transactions);

    /**
     * Returns one page of transactions matching {@code filter}, ordered by transaction date
     * descending then id descending.
     */
    PagedTransactions search(TransactionFilter filter, int page, int size);
}
