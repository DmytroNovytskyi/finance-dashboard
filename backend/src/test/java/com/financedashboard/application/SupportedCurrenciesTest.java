package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SupportedCurrenciesTest {

    @Test
    void includesTheBaseCurrencyAlwaysEvenWhenNotListed() {
        SupportedCurrencies currencies = new SupportedCurrencies("EUR", List.of("PLN", "USD"));

        assertThat(currencies.base()).isEqualTo("EUR");
        assertThat(currencies.codes()).containsExactlyInAnyOrder("EUR", "PLN", "USD");
    }

    @Test
    void normalizesAndDeduplicatesConfiguredCodes() {
        SupportedCurrencies currencies = new SupportedCurrencies("PLN", List.of("PLN", " usd ", "USD", "EUR"));

        assertThat(currencies.codes()).containsExactlyInAnyOrder("PLN", "USD", "EUR");
        assertThat(currencies.base()).isEqualTo("PLN");
    }

    @Test
    void supportsAnswersInAnyCaseAndRejectsUnknownOrNull() {
        SupportedCurrencies currencies = new SupportedCurrencies("PLN", List.of("PLN", "USD"));

        assertThat(currencies.supports("USD")).isTrue();
        assertThat(currencies.supports("usd")).isTrue();
        assertThat(currencies.supports("GBP")).isFalse();
        assertThat(currencies.supports(null)).isFalse();
    }

    @Test
    void resolveFallsBackToTheBaseForUnknownCodesAndNull() {
        SupportedCurrencies currencies = new SupportedCurrencies("PLN", List.of("PLN", "USD"));

        assertThat(currencies.resolve("USD")).isEqualTo("USD");
        assertThat(currencies.resolve(" usd ")).isEqualTo("USD");
        assertThat(currencies.resolve("GBP")).isEqualTo("PLN");
        assertThat(currencies.resolve(null)).isEqualTo("PLN");
    }

    @Test
    void defaultsToPlnAndUsd() {
        SupportedCurrencies currencies = new SupportedCurrencies("PLN", List.of("PLN", "USD"));

        assertThat(currencies.codes()).containsExactlyInAnyOrder("PLN", "USD");
    }
}
