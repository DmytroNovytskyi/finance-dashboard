package com.financedashboard.web.dto;

import com.financedashboard.application.StatisticsService.CategoryTrend;
import java.util.List;

/** API representation of one category's income/expense over time (buckets reuse the summary shape). */
public record CategoryTrendResponse(
        String baseCurrency,
        List<StatisticsSummaryResponse.TrendPoint> trend) {

    public static CategoryTrendResponse from(CategoryTrend trend) {
        return new CategoryTrendResponse(
                trend.baseCurrency(),
                trend.buckets().stream().map(StatisticsSummaryResponse.TrendPoint::from).toList());
    }
}
