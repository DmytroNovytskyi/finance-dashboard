package com.financedashboard.web.controller;

import com.financedashboard.application.StatisticsService;
import com.financedashboard.application.TrendGranularity;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.web.dto.CategorySeriesResponse;
import com.financedashboard.web.dto.CategoryTrendResponse;
import com.financedashboard.web.dto.StatisticsSummaryResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for spending statistics. */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statistics;

    /**
     * Returns the statistics summary over an inclusive date range. The range covers all accounts,
     * or is restricted to one account via {@code accountId} or to the accounts of one
     * {@code kind} (business view). Internal transfers are always excluded. {@code displayCurrency}
     * selects which stored per-transaction currency is summed (an unsupported code falls back to the
     * base currency); {@code baseCurrency} in the response mirrors the selection.
     */
    @GetMapping("/summary")
    public StatisticsSummaryResponse summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) AccountKind kind,
            @RequestParam(required = false) Integer topN,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String displayCurrency) {
        return StatisticsSummaryResponse.from(
                statistics.summary(from, to, accountId, kind, topN, TrendGranularity.from(granularity),
                        displayCurrency));
    }

    /**
     * Returns the individual transactions of one category over the inclusive range, each as a dated
     * amount in the resolved currency in ascending date order (no bucketing). Only {@code INCOME}/
     * {@code EXPENSE} transactions are included, as in the summary.
     */
    @GetMapping("/categories/{categoryId}/transactions")
    public CategorySeriesResponse categoryTransactions(
            @PathVariable Long categoryId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String displayCurrency) {
        return CategorySeriesResponse.from(
                statistics.categorySeries(categoryId, from, to, displayCurrency));
    }

    /**
     * Returns the income/expense time series of one category over the inclusive range, bucketed by
     * {@code granularity} in the resolved currency. Buckets with no transactions are absent.
     */
    @GetMapping("/categories/{categoryId}/trend")
    public CategoryTrendResponse categoryTrend(
            @PathVariable Long categoryId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String displayCurrency) {
        return CategoryTrendResponse.from(
                statistics.categoryTrend(categoryId, from, to, TrendGranularity.from(granularity),
                        displayCurrency));
    }
}
