package com.financedashboard.domain.port;

import com.financedashboard.domain.transaction.TransactionAmount;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Outbound port for the per-currency {@code transaction_amount} rows. */
public interface TransactionAmountRepository {

    /** Stores the given per-currency values. */
    void saveAll(Collection<TransactionAmount> rows);

    /** The stored amount in {@code currency} for each of the given transaction ids. */
    Map<Long, BigDecimal> findAmountsByCurrency(Collection<Long> transactionIds, String currency);

    /** All stored per-currency rows of the given transactions. */
    List<TransactionAmount> findByTransactionIds(Collection<Long> transactionIds);
}
