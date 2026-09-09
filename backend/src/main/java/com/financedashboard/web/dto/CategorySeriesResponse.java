package com.financedashboard.web.dto;

import com.financedashboard.application.StatisticsService.CategorySeries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API representation of one category's individual transactions (one dated amount per point). */
public record CategorySeriesResponse(
        String baseCurrency,
        List<Point> points) {

    /** A single dated base-currency amount of one transaction. */
    public record Point(LocalDate date, BigDecimal amount) {
    }

    public static CategorySeriesResponse from(CategorySeries series) {
        return new CategorySeriesResponse(
                series.baseCurrency(),
                series.points().stream()
                        .map(p -> new Point(p.date(), p.amount()))
                        .toList());
    }
}
