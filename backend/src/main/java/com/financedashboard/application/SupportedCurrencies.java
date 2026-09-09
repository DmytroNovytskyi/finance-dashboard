package com.financedashboard.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The set of currencies every transaction is valued in. The base currency is always included; the
 * configured {@code finance.currencies} list supplies the rest (default PLN, USD).
 */
@Component
public class SupportedCurrencies {

    private final String base;
    private final Set<String> codes;

    /** Builds the supported set from the base currency and the configured currency list. */
    public SupportedCurrencies(
            @Value("${finance.base-currency:PLN}") String baseCurrency,
            @Value("${finance.currencies:PLN,USD}") List<String> currencies) {
        this.base = normalize(baseCurrency);
        Set<String> all = new LinkedHashSet<>();
        all.add(this.base);
        for (String code : currencies) {
            if (code != null && !code.isBlank()) {
                all.add(normalize(code));
            }
        }
        this.codes = Set.copyOf(all);
    }

    /** The normalized base currency (the rate denominator for every stored amount). */
    public String base() {
        return base;
    }

    /** All supported currencies, normalized, including the base currency. */
    public Set<String> codes() {
        return codes;
    }

    /** Whether {@code code} is one of the supported currencies. */
    public boolean supports(String code) {
        return code != null && codes.contains(normalize(code));
    }

    /** Upper-cases a requested display currency when supported, otherwise the base currency. */
    public String resolve(String requested) {
        if (requested == null) {
            return base;
        }
        String code = normalize(requested);
        return codes.contains(code) ? code : base;
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
