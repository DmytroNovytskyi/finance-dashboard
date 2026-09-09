package com.financedashboard.application;

import com.financedashboard.domain.money.FxMath;
import com.financedashboard.domain.port.FxRateProvider;
import com.financedashboard.domain.port.TransactionAmountRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionAmount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Idempotently writes the {@code transaction_amount} child rows that are missing for existing
 * transactions (legacy rows imported before multi-currency, or a currency added to the supported
 * set later). A transaction whose rates cannot be resolved is skipped and can be revisited by
 * running the backfill again.
 */
@Service
@RequiredArgsConstructor
public class TransactionAmountBackfillService {

    private final TransactionRepository transactions;
    private final TransactionAmountRepository transactionAmounts;
    private final FxRateProvider fxRates;
    private final SupportedCurrencies supportedCurrencies;

    /** How a backfill pass went. */
    public record BackfillSummary(int examined, int written, int skipped) {
    }

    /** Fills missing per-currency rows for every transaction and reports what it did. */
    public BackfillSummary backfillAll() {
        List<Transaction> all = transactions.findAll();
        if (all.isEmpty()) {
            return new BackfillSummary(0, 0, 0);
        }
        List<Long> ids = all.stream().map(Transaction::getId).toList();
        Map<Long, Set<String>> present = presentByTransaction(transactionAmounts.findByTransactionIds(ids));
        Map<CodeDate, BigDecimal> rateCache = new HashMap<>();

        List<TransactionAmount> toWrite = new ArrayList<>();
        int skipped = 0;
        for (Transaction transaction : all) {
            Set<String> missing = new LinkedHashSet<>(supportedCurrencies.codes());
            missing.removeAll(present.getOrDefault(transaction.getId(), Set.of()));
            if (missing.isEmpty()) {
                continue;
            }
            List<TransactionAmount> children = childrenFor(transaction, missing, rateCache);
            if (children == null) {
                skipped++;
                continue;
            }
            toWrite.addAll(children);
        }
        if (!toWrite.isEmpty()) {
            transactionAmounts.saveAll(toWrite);
        }
        return new BackfillSummary(all.size(), toWrite.size(), skipped);
    }

    private List<TransactionAmount> childrenFor(Transaction transaction, Set<String> missing,
                                                Map<CodeDate, BigDecimal> rateCache) {
        String nativeCode = transaction.getCurrency().toUpperCase(Locale.ROOT);
        BigDecimal nativeToBase = rate(transaction.getCurrency(), transaction.getTransactionDate(), rateCache);
        if (nativeToBase == null) {
            return null;
        }
        List<TransactionAmount> children = new ArrayList<>();
        for (String code : missing) {
            if (code.equalsIgnoreCase(nativeCode)) {
                children.add(new TransactionAmount(transaction.getId(), code, transaction.getAmount()));
                continue;
            }
            BigDecimal targetToBase = rate(code, transaction.getTransactionDate(), rateCache);
            if (targetToBase == null) {
                return null;
            }
            children.add(new TransactionAmount(transaction.getId(), code,
                    FxMath.inTarget(transaction.getAmount(), nativeToBase, targetToBase)));
        }
        return children;
    }

    private BigDecimal rate(String currency, LocalDate date, Map<CodeDate, BigDecimal> cache) {
        String code = currency.toUpperCase(Locale.ROOT);
        if (code.equals(supportedCurrencies.base())) {
            return BigDecimal.ONE;
        }
        CodeDate key = new CodeDate(code, date);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        BigDecimal rate = fxRates.findRate(code, date).map(FxRateProvider.FxRate::rate).orElse(null);
        cache.put(key, rate);
        return rate;
    }

    private static Map<Long, Set<String>> presentByTransaction(List<TransactionAmount> rows) {
        Map<Long, Set<String>> present = new HashMap<>();
        for (TransactionAmount row : rows) {
            present.computeIfAbsent(row.transactionId(), id -> new LinkedHashSet<>()).add(row.currency());
        }
        return present;
    }

    private record CodeDate(String code, LocalDate date) {
    }
}
