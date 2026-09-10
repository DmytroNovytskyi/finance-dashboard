package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionAmountRepository;
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
import org.springframework.stereotype.Service;

/**
 * Use case for spending statistics over a period. Only {@code INCOME} and {@code EXPENSE}
 * transactions count (internal transfers are excluded). Each transaction contributes its stored
 * value in the requested currency (from the {@code transaction_amount} rows baked at import), so
 * no read-time conversion is needed and every supported currency is reported directly.
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
    private final TransactionAmountRepository transactionAmounts;
    private final SupportedCurrencies supportedCurrencies;

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

    /** Money totals for one time bucket of the chosen trend granularity. */
    public record TrendPoint(
            LocalDate start,
            LocalDate end,
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

    /** The complete statistics report for the requested period, in one currency. */
    public record Summary(
            LocalDate from,
            LocalDate to,
            String baseCurrency,
            Totals totals,
            List<MonthlyTotal> byMonth,
            List<CategoryTotal> byCategory,
            List<MerchantTotal> topMerchants,
            List<TrendPoint> trend) {
    }

    /** One dated amount of a single transaction in a category series. */
    public record SeriesPoint(LocalDate date, BigDecimal amount) {
    }

    /** The individual transactions of one category over a range, as dated amounts in one currency. */
    public record CategorySeries(
            String baseCurrency,
            List<SeriesPoint> points) {
    }

    /** One category's income/expense/net per time bucket over a range. */
    public record CategoryTrend(
            String baseCurrency,
            List<TrendPoint> buckets) {
    }

    /** {@link #summary(LocalDate, LocalDate, Long, AccountKind, Integer, TrendGranularity)} at month granularity. */
    public Summary summary(LocalDate from, LocalDate to, Long accountId, AccountKind kind, Integer topN) {
        return summary(from, to, accountId, kind, topN, TrendGranularity.MONTH);
    }

    /**
     * Computes the summary for the inclusive date range. The range is restricted to one account
     * when {@code accountId} is set, otherwise to accounts of the given {@code kind} when set,
     * otherwise to all accounts. {@code topN} bounds the merchant list (1..50, default 10).
     * {@code granularity} selects the time buckets of the returned {@code trend}.
     */
    public Summary summary(LocalDate from, LocalDate to, Long accountId, AccountKind kind, Integer topN,
                           TrendGranularity granularity) {
        return summary(from, to, accountId, kind, topN, granularity, null);
    }

    /**
     * Computes the summary for the inclusive date range as above. {@code displayCurrency} selects
     * which stored per-transaction currency is summed (an unsupported request falls back to the
     * base currency); the reported {@code baseCurrency} mirrors it. Internal transfers are always
     * excluded.
     */
    public Summary summary(LocalDate from, LocalDate to, Long accountId, AccountKind kind, Integer topN,
                           TrendGranularity granularity, String displayCurrency) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        String code = supportedCurrencies.resolve(displayCurrency);
        Collection<Long> accountIds = resolveAccountIds(accountId, kind);
        List<Transaction> rows = (accountIds == null || !accountIds.isEmpty())
                ? transactions.findStatistical(from, to, accountIds)
                : List.of();
        Map<Long, BigDecimal> amounts = amountsFor(rows, code);

        Map<Long, Category> categoryById = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        Sums totals = new Sums();
        Map<YearMonth, Sums> byMonth = new LinkedHashMap<>();
        Map<LocalDate, Sums> byBucket = new LinkedHashMap<>();
        Map<Long, Sums> byCategory = new LinkedHashMap<>();
        Map<String, Sums> byMerchant = new LinkedHashMap<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;
        long uncategorized = 0;

        for (Transaction tx : rows) {
            LocalDate date = tx.getTransactionDate();
            minDate = minDate == null || date.isBefore(minDate) ? date : minDate;
            maxDate = maxDate == null || date.isAfter(maxDate) ? date : maxDate;
            BigDecimal value = amounts.get(tx.getId());
            if (value == null) {
                continue;
            }
            totals.add(value);
            byMonth.computeIfAbsent(YearMonth.from(date), m -> new Sums()).add(value);
            TrendGranularity.Bucket bucket = granularity.bucketOf(date);
            byBucket.computeIfAbsent(bucket.start(), k -> new Sums()).add(value);
            byCategory.computeIfAbsent(tx.getCategoryId(), c -> new Sums()).add(value);
            String merchant = tx.getMerchant() == null || tx.getMerchant().isBlank()
                    ? NO_MERCHANT_LABEL : tx.getMerchant().trim();
            byMerchant.computeIfAbsent(merchant, m -> new Sums()).add(value);
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

        List<TrendPoint> trend = byBucket.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    LocalDate start = e.getKey();
                    LocalDate end = granularity.bucketOf(start).end();
                    Sums sums = e.getValue();
                    return new TrendPoint(start, end, sums.income, sums.expense, sums.net());
                })
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

        return new Summary(from, to, code, total, monthly, categoryTotals, topMerchants, trend);
    }

    /**
     * Returns each {@code INCOME}/{@code EXPENSE} transaction of one category over the inclusive
     * range as one dated amount in the resolved currency, in ascending date order. No bucketing is
     * applied: each point is a single transaction.
     */
    public CategorySeries categorySeries(Long categoryId, LocalDate from, LocalDate to) {
        return categorySeries(categoryId, from, to, null);
    }

    /** {@link #categorySeries(Long, LocalDate, LocalDate)} in the currency selected by {@code displayCurrency}. */
    public CategorySeries categorySeries(Long categoryId, LocalDate from, LocalDate to, String displayCurrency) {
        if (categories.findById(categoryId).isEmpty()) {
            throw new NotFoundException("Category " + categoryId + " not found");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        String code = supportedCurrencies.resolve(displayCurrency);
        List<Transaction> matching = new ArrayList<>();
        for (Transaction tx : transactions.findStatistical(from, to, null)) {
            if (categoryId.equals(tx.getCategoryId())) {
                matching.add(tx);
            }
        }
        Map<Long, BigDecimal> amounts = amountsFor(matching, code);
        List<SeriesPoint> points = matching.stream()
                .map(tx -> new SeriesPoint(tx.getTransactionDate(), amounts.get(tx.getId())))
                .filter(p -> p.amount() != null)
                .toList();
        return new CategorySeries(code, points);
    }

    /**
     * Computes the income/expense time series of one category over the inclusive range, bucketed by
     * {@code granularity}, in the resolved currency. Buckets with no transactions are absent.
     */
    public CategoryTrend categoryTrend(Long categoryId, LocalDate from, LocalDate to,
                                       TrendGranularity granularity) {
        return categoryTrend(categoryId, from, to, granularity, null);
    }

    /** {@link #categoryTrend(Long, LocalDate, LocalDate, TrendGranularity)} in the selected currency. */
    public CategoryTrend categoryTrend(Long categoryId, LocalDate from, LocalDate to,
                                       TrendGranularity granularity, String displayCurrency) {
        if (categories.findById(categoryId).isEmpty()) {
            throw new NotFoundException("Category " + categoryId + " not found");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        String code = supportedCurrencies.resolve(displayCurrency);
        List<Transaction> matching = new ArrayList<>();
        for (Transaction tx : transactions.findStatistical(from, to, null)) {
            if (categoryId.equals(tx.getCategoryId())) {
                matching.add(tx);
            }
        }
        Map<Long, BigDecimal> amounts = amountsFor(matching, code);

        Map<LocalDate, Sums> byBucket = new LinkedHashMap<>();
        for (Transaction tx : matching) {
            BigDecimal value = amounts.get(tx.getId());
            if (value == null) {
                continue;
            }
            TrendGranularity.Bucket bucket = granularity.bucketOf(tx.getTransactionDate());
            byBucket.computeIfAbsent(bucket.start(), k -> new Sums()).add(value);
        }
        List<TrendPoint> buckets = byBucket.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new TrendPoint(e.getKey(), granularity.bucketOf(e.getKey()).end(),
                        e.getValue().income, e.getValue().expense, e.getValue().net()))
                .toList();
        return new CategoryTrend(code, buckets);
    }

    /** The stored amount in {@code code} for each transaction in {@code rows}. */
    private Map<Long, BigDecimal> amountsFor(List<Transaction> rows, String code) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(Transaction::getId).toList();
        return transactionAmounts.findAmountsByCurrency(ids, code);
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

    /** Accumulates signed amounts into income, expense (magnitude) and a count. */
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
