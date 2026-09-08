package com.financedashboard.infrastructure.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * HTTP adapter for the official NBP mid-rate API (table A). A single rate lookup:
 * {@code GET {base}/api/exchangerates/rates/a/{currency}/{yyyy-MM-dd}/}.
 */
@Component
public class NbpClient {

    private final RestClient restClient;

    public NbpClient(@Value("${finance.fx.nbp.base-url:https://api.nbp.pl}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Returns the NBP table-A mid rate for {@code currency} on {@code date}, if published. */
    public Optional<BigDecimal> midRate(String currency, LocalDate date) {
        try {
            NbpResponse response = restClient.get()
                    .uri("/api/exchangerates/rates/a/{currency}/{date}/",
                            currency.toUpperCase(Locale.ROOT), date)
                    .retrieve()
                    .body(NbpResponse.class);
            if (response == null || response.rates().isEmpty() || response.rates().get(0).mid() == null) {
                return Optional.empty();
            }
            return Optional.of(response.rates().get(0).mid());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /** NBP JSON response for a single day: {@code {table, currency, code, rates:[{no, effectiveDate, mid}]}}. */
    record NbpResponse(String table, String currency, String code, List<NbpRate> rates) {
        record NbpRate(String no, LocalDate effectiveDate, BigDecimal mid) {
        }
    }
}
