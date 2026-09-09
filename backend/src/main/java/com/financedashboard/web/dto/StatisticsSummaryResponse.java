package com.financedashboard.web.dto;

import com.financedashboard.application.StatisticsService.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API representation of a statistics summary. */
public record StatisticsSummaryResponse(
        LocalDate from,
        LocalDate to,
        String baseCurrency,
        Totals totals,
        List<Monthly> byMonth,
        List<Category> byCategory,
        List<Merchant> topMerchants,
        List<TrendPoint> trend,
        long unconverted) {

    public static StatisticsSummaryResponse from(Summary s) {
        return new StatisticsSummaryResponse(
                s.from(),
                s.to(),
                s.baseCurrency(),
                Totals.from(s.totals()),
                s.byMonth().stream().map(Monthly::from).toList(),
                s.byCategory().stream().map(Category::from).toList(),
                s.topMerchants().stream().map(Merchant::from).toList(),
                s.trend().stream().map(TrendPoint::from).toList(),
                s.unconverted());
    }

    /** API representation of the aggregate totals. */
    public record Totals(
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net,
            long count,
            long uncategorizedCount,
            BigDecimal avgExpensePerDay) {

        static Totals from(com.financedashboard.application.StatisticsService.Totals t) {
            return new Totals(t.income(), t.expense(), t.net(),
                    t.count(), t.uncategorizedCount(), t.avgExpensePerDay());
        }
    }

    /** API representation of one month's totals. */
    public record Monthly(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {

        static Monthly from(com.financedashboard.application.StatisticsService.MonthlyTotal m) {
            return new Monthly(m.month(), m.income(), m.expense(), m.net());
        }
    }

    /** API representation of one category's totals. */
    public record Category(
            Long categoryId,
            String categoryName,
            String color,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net) {

        static Category from(com.financedashboard.application.StatisticsService.CategoryTotal c) {
            return new Category(c.categoryId(), c.categoryName(), c.color(),
                    c.income(), c.expense(), c.net());
        }
    }

    /** API representation of one merchant's totals. */
    public record Merchant(
            String merchant,
            long count,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net) {

        static Merchant from(com.financedashboard.application.StatisticsService.MerchantTotal m) {
            return new Merchant(m.merchant(), m.count(), m.income(), m.expense(), m.net());
        }
    }

    /** API representation of one time bucket of the trend. */
    public record TrendPoint(
            LocalDate start,
            LocalDate end,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net) {

        static TrendPoint from(com.financedashboard.application.StatisticsService.TrendPoint p) {
            return new TrendPoint(p.start(), p.end(), p.income(), p.expense(), p.net());
        }
    }
}
