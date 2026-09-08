package com.financedashboard.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves a currency→base conversion rate for a value date. Implementations are DB-first: a rate
 * stored in {@code fx_rate} is reused; the external source is only consulted as a fallback.
 */
public interface FxRateProvider {

    /** A conversion rate and the date it actually applies to. */
    record FxRate(BigDecimal rate, LocalDate date) {
    }

    /**
     * Returns the rate to convert an amount in {@code currency} into the configured base currency
     * for {@code date}, if one can be resolved.
     */
    Optional<FxRate> findRate(String currency, LocalDate date);
}
