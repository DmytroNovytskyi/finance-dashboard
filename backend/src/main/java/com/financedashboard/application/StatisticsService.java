package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Use case for spending statistics over a period. Only {@code INCOME} and {@code EXPENSE}
 * transactions count (internal transfers are excluded); amounts are summed in the base currency
 * using the stored {@link Transaction#getBaseAmount()}. Rows without a base amount (a foreign
 * currency for which no rate was available at import) are reported in the {@code unconverted}
 * counter instead of the money buckets.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final int DEFAULT_TOP_MERCHANTS = 10;
    private static final int MAX_TOP_MERCHANTS = 50;
    private static final String NO_MERCHANT_LABEL = "(no merchant)";
    private static final String UNCATEGORIZED_LABEL = "(uncategorized)";

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;

    @Value("${finance.base-currency:PLN}")
    private String baseCurrency;

    /** Aggregate money and count totals for the period. {@code expense} is a positive magnitude. */
    public record Totals(
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net,
            long count,
            long uncategorizedCount,
            BigDecimal avgExpensePerDay) {
    }

    /** Money totals for one calendar month; {@code month} is an ISO {@code yyyy-MM} string. */
    public record MonthlyTotal(
            String month,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net) {
    }

    /** Money totals for one category; {@code categoryId} is null for uncategorized rows. */
    public record CategoryTotal(
            Long categoryId,
            String categoryName,
            String color,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net) {
    }

    /** Money totals for one merchant; transactions without a merchant are bucketed together. */
    public record MerchantTotal(
            String merchant,
            long count,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net) {
    }

    /** The complete statistics report for the requested period. */
    public record Summary(
            LocalDate from,
            LocalDate to,
            String baseCurrency,
            Totals totals,
            List<MonthlyTotal> byMonth,
            List<CategoryTotal> byCategory,
            List<MerchantTotal> topMerchants,
            long unconverted) {
    }

    /**
     * Computes the summary for the inclusive date range. The range is restricted to one account
     * when {@code accountId} is set, otherwise to accounts of the given {@code kind} when set,
     * otherwise to all accounts. {@code topN} bounds the merchant list (1..50, default 10).
     */
    public Summary summary(LocalDate from, LocalDate to, Long accountId, AccountKind kind, Integer topN) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        Collection<Long> accountIds = resolveAccountIds(accountId, kind);
        List<Transaction> rows = (accountIds == null || !accountIds.isEmpty())
                ? transactions.findNonTransfers(from, to, accountIds)
                : List.of();

        Map<Long, Category> categoryById = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Sums totals = new Sums();
        Map<YearMonth, Sums> byMonth = new LinkedHashMap<>();
        Map<Long, Sums> byCategory = new LinkedHashMap<>();
        Map<String, Sums> byMerchant = new LinkedHashMap<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;
        long unconverted = 0;
        long uncategorized = 0;

        for (Transaction tx : rows) {
            LocalDate date = tx.getTransactionDate();
            minDate = minDate == null || date.isBefore(minDate) ? date : minDate;
            maxDate = maxDate == null || date.isAfter(maxDate) ? date : maxDate;
            BigDecimal base = inBaseCurrency(tx);
            if (base == null) {
                unconverted++;
                continue;
            }
            totals.add(base);
            byMonth.computeIfAbsent(YearMonth.from(date), m -> new Sums()).add(base);
            byCategory.computeIfAbsent(tx.getCategoryId(), c -> new Sums()).add(base);
            String merchant = tx.getMerchant() == null || tx.getMerchant().isBlank()
                    ? NO_MERCHANT_LABEL : tx.getMerchant().trim();
            byMerchant.computeIfAbsent(merchant, m -> new Sums()).add(base);
            if (tx.getCategoryId() == null) {
                uncategorized++;
            }
        }

        BigDecimal avgPerDay = totals.expense.divide(
                BigDecimal.valueOf(periodDays(from, to, minDate, maxDate)), 2, RoundingMode.HALF_UP);
        Totals total = new Totals(totals.income, totals.expense, totals.net(),
                totals.count, uncategorized, avgPerDay);

        List<MonthlyTotal> monthly = byMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new MonthlyTotal(e.getKey().toString(), e.getValue().income,
                        e.getValue().expense, e.getValue().net()))
                .toList();

        List<CategoryTotal> categoryTotals = new ArrayList<>();
        byCategory.forEach((categoryId, sums) -> {
            Category category = categoryId == null ? null : categoryById.get(categoryId);
            String name = category == null ? UNCATEGORIZED_LABEL : category.getName();
            String color = category == null ? null : category.getColor();
            categoryTotals.add(new CategoryTotal(categoryId, name, color,
                    sums.income, sums.expense, sums.net()));
        });
        categoryTotals.sort(Comparator.comparing(CategoryTotal::expense).reversed()
                .thenComparing(CategoryTotal::categoryName));

        List<MerchantTotal> topMerchants = byMerchant.entrySet().stream()
                .map(e -> new MerchantTotal(e.getKey(), e.getValue().count, e.getValue().income,
                        e.getValue().expense, e.getValue().net()))
                .sorted(Comparator.comparing(MerchantTotal::expense).reversed()
                        .thenComparing(Comparator.comparingLong(MerchantTotal::count).reversed())
                        .thenComparing(MerchantTotal::merchant))
                .limit(clampTopN(topN))
                .toList();

        return new Summary(from, to, baseCurrency, total, monthly, categoryTotals, topMerchants, unconverted);
    }

    private Collection<Long> resolveAccountIds(Long accountId, AccountKind kind) {
        if (accountId != null) {
            if (!accounts.existsById(accountId)) {
                throw new NotFoundException("Account " + accountId + " not found");
            }
            return List.of(accountId);
        }
        if (kind != null) {
            return accounts.findByKind(kind).stream().map(Account::getId).toList();
        }
        return null;
    }

    private static BigDecimal inBaseCurrency(Transaction tx) {
        return tx.getBaseAmount();
    }

    private static long periodDays(LocalDate from, LocalDate to, LocalDate minDate, LocalDate maxDate) {
        LocalDate lo = from == null ? minDate : from;
        LocalDate hi = to == null ? maxDate : to;
        if (lo == null || hi == null) {
            return 1;
        }
        return Math.max(1, ChronoUnit.DAYS.between(lo, hi) + 1);
    }

    private static int clampTopN(Integer topN) {
        int top = topN == null ? DEFAULT_TOP_MERCHANTS : topN;
        return Math.min(MAX_TOP_MERCHANTS, Math.max(1, top));
    }

    /** Accumulates signed base-currency amounts into income, expense (magnitude) and a count. */
    private static final class Sums {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
        private long count;

        void add(BigDecimal amount) {
            if (amount.signum() >= 0) {
                income = income.add(amount);
            } else {
                expense = expense.subtract(amount);
            }
            count++;
        }

        private BigDecimal net() {
            return income.subtract(expense);
        }
    }
}
