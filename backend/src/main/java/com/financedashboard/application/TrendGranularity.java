package com.financedashboard.application;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * The time bucket used to break a statistics trend into points. {@link #bucketOf(LocalDate)}
 * returns the inclusive start/end of the bucket containing a date.
 */
public enum TrendGranularity {

    DAY,
    WEEK,
    MONTH,
    QUARTER,
    YEAR;

    /** An inclusive day range {@code start..end}. */
    public record Bucket(LocalDate start, LocalDate end) {
    }

    /** Resolves the bucket containing {@code date} for this granularity. */
    public Bucket bucketOf(LocalDate date) {
        return switch (this) {
            case DAY -> new Bucket(date, date);
            case WEEK -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                yield new Bucket(start, start.plusDays(6));
            }
            case MONTH -> {
                LocalDate start = date.withDayOfMonth(1);
                yield new Bucket(start, start.with(TemporalAdjusters.lastDayOfMonth()));
            }
            case QUARTER -> {
                LocalDate firstOfMonth = date.withDayOfMonth(1);
                int quarterFirstMonth = ((firstOfMonth.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(date.getYear(), quarterFirstMonth, 1);
                yield new Bucket(start, start.plusMonths(3).minusDays(1));
            }
            case YEAR -> {
                LocalDate start = LocalDate.of(date.getYear(), 1, 1);
                yield new Bucket(start, LocalDate.of(date.getYear(), 12, 31));
            }
        };
    }

    /** Parses a granularity name, case-insensitively; an empty/blank value means {@link #MONTH}. */
    public static TrendGranularity from(String value) {
        if (value == null || value.isBlank()) {
            return MONTH;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "granularity must be one of day, week, month, quarter, year");
        }
    }
}
