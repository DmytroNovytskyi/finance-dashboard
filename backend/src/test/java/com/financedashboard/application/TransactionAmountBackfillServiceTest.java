package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.TransactionAmountBackfillService.BackfillSummary;
import com.financedashboard.domain.port.FxRateProvider;
import com.financedashboard.domain.port.FxRateProvider.FxRate;
import com.financedashboard.domain.port.TransactionAmountRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionAmount;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionAmountBackfillServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 5);

    @Mock
    private TransactionRepository transactions;
    @Mock
    private TransactionAmountRepository transactionAmounts;
    @Mock
    private FxRateProvider fxRates;

    private TransactionAmountBackfillService service;

    @BeforeEach
    void setUp() {
        service = new TransactionAmountBackfillService(transactions, transactionAmounts, fxRates,
                new SupportedCurrencies("PLN", List.of("PLN", "USD")));
    }

    private Transaction tx(long id, String currency, String amount) {
        return Transaction.builder()
                .id(id)
                .statementId(1L)
                .accountId(1L)
                .transactionDate(DATE)
                .amount(new BigDecimal(amount))
                .currency(currency)
                .nature(TransactionNature.EXPENSE)
                .build();
    }

    @Test
    void writesMissingChildrenForEveryTransaction() {
        when(transactions.findAll()).thenReturn(List.of(tx(1L, "PLN", "-250.50")));
        when(transactionAmounts.findByTransactionIds(List.of(1L))).thenReturn(List.of());
        when(fxRates.findRate("USD", DATE)).thenReturn(Optional.of(new FxRate(new BigDecimal("4.0"), DATE)));

        BackfillSummary summary = service.backfillAll();

        assertThat(summary.examined()).isEqualTo(1);
        assertThat(summary.written()).isEqualTo(2);
        assertThat(summary.skipped()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionAmount>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionAmounts).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new TransactionAmount(1L, "PLN", new BigDecimal("-250.50")),
                new TransactionAmount(1L, "USD", new BigDecimal("-62.6250")));
    }

    @Test
    void keepsExistingChildrenAndWritesOnlyTheMissingCurrency() {
        when(transactions.findAll()).thenReturn(List.of(tx(1L, "PLN", "-250.50")));
        when(transactionAmounts.findByTransactionIds(List.of(1L)))
                .thenReturn(List.of(new TransactionAmount(1L, "PLN", new BigDecimal("-250.50"))));
        when(fxRates.findRate("USD", DATE)).thenReturn(Optional.of(new FxRate(new BigDecimal("4.0"), DATE)));

        BackfillSummary summary = service.backfillAll();

        assertThat(summary.written()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionAmount>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionAmounts).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new TransactionAmount(1L, "USD", new BigDecimal("-62.6250")));
    }

    @Test
    void leavesACompleteTransactionUntouched() {
        when(transactions.findAll()).thenReturn(List.of(tx(1L, "PLN", "-250.50")));
        when(transactionAmounts.findByTransactionIds(List.of(1L))).thenReturn(List.of(
                new TransactionAmount(1L, "PLN", new BigDecimal("-250.50")),
                new TransactionAmount(1L, "USD", new BigDecimal("-62.6250"))));

        BackfillSummary summary = service.backfillAll();

        assertThat(summary.examined()).isEqualTo(1);
        assertThat(summary.written()).isZero();
        assertThat(summary.skipped()).isZero();
        verify(transactionAmounts, never()).saveAll(any());
        verify(fxRates, never()).findRate(any(), any());
    }

    @Test
    void skipsATransactionWhoseRateCannotBeResolved() {
        when(transactions.findAll()).thenReturn(List.of(tx(1L, "PLN", "-250.50")));
        when(transactionAmounts.findByTransactionIds(List.of(1L))).thenReturn(List.of());
        when(fxRates.findRate("USD", DATE)).thenReturn(Optional.empty());

        BackfillSummary summary = service.backfillAll();

        assertThat(summary.examined()).isEqualTo(1);
        assertThat(summary.written()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        verify(transactionAmounts, never()).saveAll(any());
    }

    @Test
    void foreignNativeCurrencyBecomesExactAndOthersAreConverted() {
        when(transactions.findAll()).thenReturn(List.of(tx(1L, "USD", "-50")));
        when(transactionAmounts.findByTransactionIds(List.of(1L))).thenReturn(List.of());
        when(fxRates.findRate("USD", DATE)).thenReturn(Optional.of(new FxRate(new BigDecimal("4.0"), DATE)));

        BackfillSummary summary = service.backfillAll();

        assertThat(summary.written()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionAmount>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionAmounts).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new TransactionAmount(1L, "USD", new BigDecimal("-50")),
                new TransactionAmount(1L, "PLN", new BigDecimal("-200.0000")));
    }

    @Test
    void emptyDatabaseWritesNothing() {
        when(transactions.findAll()).thenReturn(List.of());

        BackfillSummary summary = service.backfillAll();

        assertThat(summary).isEqualTo(new BackfillSummary(0, 0, 0));
        verify(transactionAmounts, never()).saveAll(any());
    }
}
