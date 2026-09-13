package com.financedashboard.domain.port;

import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.domain.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Returns the transactions that count towards statistics — everything whose nature is not
     * excluded ({@link TransactionNature#excludedFromStatistics()}) — with a transaction date
     * within the inclusive range (bounds optional), restricted to the given accounts, oldest
     * first. {@code null} bounds or accountIds mean no restriction.
     */
    List<Transaction> findStatistical(LocalDate from, LocalDate to, Collection<Long> accountIds);

    /**
     * Returns the refund legs with a transaction date within the inclusive range (bounds optional),
     * restricted to the given accounts, oldest first. {@code null} bounds or accountIds mean no
     * restriction. Their nature excludes them from {@link #findStatistical}, but the statistics fold
     * each group back in as its net, so they have to be read separately.
     */
    List<Transaction> findRefunds(LocalDate from, LocalDate to, Collection<Long> accountIds);

    /** Returns transactions belonging to any of the given transfer groups. */
    List<Transaction> findByTransferGroupIds(Collection<UUID> transferGroupIds);

    /** Returns transactions belonging to any of the given refund groups. */
    List<Transaction> findByRefundGroupIds(Collection<UUID> refundGroupIds);

    /** Returns whether any transaction belongs to the statement with the given id. */
    boolean existsByStatementId(Long statementId);

    /** Returns whether any transaction belongs to the account with the given id. */
    boolean existsByAccountId(Long accountId);

    /** Returns all transactions that belong to the statement with the given id. */
    List<Transaction> findByStatementId(Long statementId);

    /** Returns all transactions of the account with the given id, oldest first. */
    List<Transaction> findByAccountId(Long accountId);

    /**
     * Returns the number of stored transactions per statement, keyed by statement id. Statements
     * with no transactions are absent from the map.
     */
    Map<Long, Long> countByStatementIds(Collection<Long> statementIds);

    /** Deletes the given transactions. */
    void deleteAll(Collection<Transaction> transactions);

    /** Returns all transactions, oldest first. Used by the multi-currency backfill. */
    List<Transaction> findAll();

    /**
     * Returns all transactions that count towards statistics, oldest first. Used as the candidate
     * pool for transfer pairing, which must never pick up a row that is already excluded.
     */
    List<Transaction> findAllStatistical();

    /**
     * Returns transactions that have no category and are not excluded by nature, oldest first.
     * Candidates for applying merchant default rules.
     */
    List<Transaction> findUncategorized();

    /**
     * Returns transactions that carry a category and are not excluded by nature, oldest first.
     * Candidates for the bulk "clear category" actions.
     */
    List<Transaction> findCategorized();

    /**
     * Returns one page of transactions matching {@code filter}, ordered by transaction date
     * descending then id descending.
     */
    PagedTransactions search(TransactionFilter filter, int page, int size);

    /**
     * Returns one page of transactions matching {@code filter}, ordered by {@code order}; id
     * descending is always the tie-breaker.
     */
    PagedTransactions search(TransactionFilter filter, int page, int size, TransactionOrder order);
}
