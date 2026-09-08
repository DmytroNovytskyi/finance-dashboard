package com.financedashboard.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NbpFxRateProviderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Mock
    private FxRateJpaRepository store;
    @Mock
    private NbpClient client;

    @InjectMocks
    private NbpFxRateProvider provider;

    @BeforeEach
    void setBaseCurrency() {
        ReflectionTestUtils.setField(provider, "baseCurrency", "PLN");
    }

    @Test
    void reusesStoredRateWithoutCallingClient() {
        when(store.findByCurrencyAndRateDateAndBaseCurrency("USD", DAY, "PLN"))
                .thenReturn(Optional.of(entity(DAY, "3.50000000")));

        Optional<com.financedashboard.domain.port.FxRateProvider.FxRate> rate =
                provider.findRate("USD", DAY);

        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("3.5");
        assertThat(rate.get().date()).isEqualTo(DAY);
        verify(client, never()).midRate(any(), any());
    }

    @Test
    void returnsOneForBaseCurrencyItself() {
        assertThat(provider.findRate("PLN", DAY))
                .contains(new com.financedashboard.domain.port.FxRateProvider.FxRate(
                        BigDecimal.ONE, DAY));
        verify(store, never()).findByCurrencyAndRateDateAndBaseCurrency(any(), any(), any());
    }

    @Test
    void fetchesAndPersistsRateWhenMissing() {
        when(store.findByCurrencyAndRateDateAndBaseCurrency("USD", DAY, "PLN"))
                .thenReturn(Optional.empty());
        when(client.midRate("USD", DAY)).thenReturn(Optional.of(new BigDecimal("4.0000")));

        Optional<com.financedashboard.domain.port.FxRateProvider.FxRate> rate =
                provider.findRate("USD", DAY);

        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("4.0");
        verify(store).save(any(FxRateEntity.class));
    }

    @Test
    void fallsBackToLastPublishedRate() {
        when(store.findByCurrencyAndRateDateAndBaseCurrency(eq("USD"), any(), eq("PLN")))
                .thenReturn(Optional.empty());
        when(client.midRate("USD", DAY)).thenReturn(Optional.empty());
        when(client.midRate("USD", DAY.minusDays(1))).thenReturn(Optional.empty());
        when(client.midRate("USD", DAY.minusDays(2)))
                .thenReturn(Optional.of(new BigDecimal("4.1111")));

        Optional<com.financedashboard.domain.port.FxRateProvider.FxRate> rate =
                provider.findRate("USD", DAY);

        assertThat(rate).isPresent();
        assertThat(rate.get().date()).isEqualTo(DAY.minusDays(2));
        assertThat(rate.get().rate()).isEqualByComparingTo("4.1111");
    }

    @Test
    void returnsEmptyWhenNoRateFoundWithinLookback() {
        when(store.findByCurrencyAndRateDateAndBaseCurrency("USD", DAY, "PLN"))
                .thenReturn(Optional.empty());
        when(client.midRate(eq("USD"), any())).thenReturn(Optional.empty());

        assertThat(provider.findRate("USD", DAY)).isEmpty();
        verify(store, never()).save(any());
    }

    private static FxRateEntity entity(LocalDate date, String rate) {
        FxRateEntity entity = new FxRateEntity();
        entity.setCurrency("USD");
        entity.setRateDate(date);
        entity.setRate(new BigDecimal(rate));
        entity.setBaseCurrency("PLN");
        entity.setSource("NBP");
        return entity;
    }
}
