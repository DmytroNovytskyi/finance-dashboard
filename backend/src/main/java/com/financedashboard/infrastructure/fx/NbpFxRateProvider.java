package com.financedashboard.infrastructure.fx;

import com.financedashboard.domain.port.FxRateProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB-first {@link FxRateProvider}. Lookup order: the {@code fx_rate} table, then the NBP API; a
 * rate is fetched at most once (weekend/holiday gaps resolve to the last published rate on or
 * before the requested date) and persisted for reuse.
 */
@Component
@RequiredArgsConstructor
public class NbpFxRateProvider implements FxRateProvider {

    private static final int MAX_LOOKBACK_DAYS = 21;

    private final FxRateJpaRepository store;
    private final NbpClient client;

    @Value("${finance.base-currency:PLN}")
    private String baseCurrency;

    @Override
    public Optional<FxRate> findRate(String currency, LocalDate date) {
        String code = currency.toUpperCase(Locale.ROOT);
        String base = baseCurrency.toUpperCase(Locale.ROOT);
        if (code.equals(base)) {
            return Optional.of(new FxRate(BigDecimal.ONE, date));
        }
        return store.findByCurrencyAndRateDateAndBaseCurrency(code, date, base)
                .map(entity -> new FxRate(entity.getRate(), entity.getRateDate()))
                .or(() -> fetchAndStore(code, date, base));
    }

    private Optional<FxRate> fetchAndStore(String code, LocalDate date, String base) {
        for (int back = 0; back <= MAX_LOOKBACK_DAYS; back++) {
            LocalDate day = date.minusDays(back);
            Optional<BigDecimal> mid = client.midRate(code, day);
            if (mid.isPresent()) {
                FxRateEntity entity = new FxRateEntity();
                entity.setCurrency(code);
                entity.setRateDate(day);
                entity.setRate(mid.get());
                entity.setBaseCurrency(base);
                entity.setSource("NBP");
                store.save(entity);
                return Optional.of(new FxRate(mid.get(), day));
            }
        }
        return Optional.empty();
    }
}
