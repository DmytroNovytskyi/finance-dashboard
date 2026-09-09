package com.financedashboard.web.controller;

import com.financedashboard.application.StatisticsService;
import com.financedashboard.application.TrendGranularity;
import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.web.dto.StatisticsSummaryResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
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
     * {@code kind} (business view). Internal transfers are always excluded.
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
            @RequestParam(required = false) String granularity) {
        return StatisticsSummaryResponse.from(
                statistics.summary(from, to, accountId, kind, topN, TrendGranularity.from(granularity)));
    }
}
