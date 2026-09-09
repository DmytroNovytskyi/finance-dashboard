package com.financedashboard.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the multi-currency backfill once at startup when {@code finance.fx.backfill-on-startup} is
 * enabled (default off). Intended as a one-time step for databases imported before multi-currency
 * storage, and as the path for filling a newly added supported currency.
 */
@Component
@ConditionalOnProperty(name = "finance.fx.backfill-on-startup", havingValue = "true")
@RequiredArgsConstructor
public class FxBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FxBackfillRunner.class);

    private final TransactionAmountBackfillService backfill;

    @Override
    public void run(ApplicationArguments args) {
        TransactionAmountBackfillService.BackfillSummary summary = backfill.backfillAll();
        log.info("Multi-currency backfill finished: examined={} written={} skipped={}",
                summary.examined(), summary.written(), summary.skipped());
    }
}
