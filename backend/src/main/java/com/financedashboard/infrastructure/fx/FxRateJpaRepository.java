package com.financedashboard.infrastructure.fx;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code fx_rate} table. */
public interface FxRateJpaRepository extends JpaRepository<FxRateEntity, Long> {

    Optional<FxRateEntity> findByCurrencyAndRateDateAndBaseCurrency(
            String currency, LocalDate rateDate, String baseCurrency);
}
