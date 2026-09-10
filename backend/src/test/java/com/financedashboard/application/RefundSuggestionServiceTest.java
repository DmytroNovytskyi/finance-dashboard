package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.RefundSuggestionService.SuggestedRefund;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefundSuggestionServiceTest {

    private static final String REVERSAL = "PRZELEW Bank Pekao S.A. ZWROT NIEROZLICZONEJ TRANSAKCJI "
            + "NA KARTĘ **** **** 1111 2222 ";
    private static final String CANCELLATION = "PRZELEW Bank Pekao S.A. ANULOWANIE TRANSAKCJI "
            + "NA KARTĘ **** **** 1111 2222 WYKONANEJ: ";
    private static final String TAX_REFUND = "PRZELEW KRAJOWY MIĘDZYBANKOWY EXAMPLE MERCHANT "
            + "MIASTO 00-000 ULICA 1 99999999 Zwrot z podatku VAT 5/2026 "
            + "? ? /KL/02 SACC 10000000  10000000000000000000000000 Nr ref.: 0XX0000000000000";

    @Mock
    private TransactionRepository transactions;
    @Mock
    private TransactionEditService edit;

    @InjectMocks
    private RefundSuggestionService service;

    @Test
    void matchesThePurchaseTheBankNamedInTheReversal() {
        Transaction purchase = expense(1L, 1L, "-12.34", "2026-03-05",
                "EXAMPLE SHOP       \\Somewhere     XX", null);
        Transaction refund = income(2L, 1L, "12.34", "2026-03-05",
                CANCELLATION + "example-shop \\Somewhere    DN. 05/03/2026 KRAJ WYKONANIA OPERACJI");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        List<SuggestedRefund> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).purchaseTransactionId()).isEqualTo(1L);
        assertThat(suggestions.get(0).refundTransactionId()).isEqualTo(2L);
        assertThat(suggestions.get(0).accountId()).isEqualTo(1L);
        assertThat(suggestions.get(0).amount()).isEqualByComparingTo("12.34");
        assertThat(suggestions.get(0).merchant()).isEqualTo("EXAMPLE SHOP \\Somewhere XX");
        assertThat(suggestions.get(0).purchaseDate()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(suggestions.get(0).reason()).isEqualTo("ANCHORED");
    }

    @Test
    void matchesTheAnchoredPurchaseOverANearerUnanchoredOne() {
        Transaction anchored = expense(1L, 1L, "-321.00", "2026-05-04", "Example Store", null);
        Transaction nearer = expense(2L, 1L, "-321.00", "2026-05-07", "Somewhere else", null);
        Transaction refund = income(3L, 1L, "321.00", "2026-05-08",
                CANCELLATION + "Example Store \\Somewhere        DN. 04/05/2026 KRAJ WYKONANIA OPERACJI");
        when(transactions.findAllStatistical()).thenReturn(List.of(anchored, nearer, refund));

        List<SuggestedRefund> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).purchaseTransactionId()).isEqualTo(1L);
    }

    @Test
    void fallsBackToAmountWhenTheAnchoredDateMatchesNoPurchase() {
        Transaction purchase = expense(1L, 1L, "-7.50", "2026-08-01", "EXAMPLE GAMES", null);
        Transaction refund = income(2L, 1L, "7.50", "2026-08-07",
                REVERSAL + "WYKONANEJ DN. 07/08/2026 SACC 10000000");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        List<SuggestedRefund> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).reason()).isEqualTo("AMOUNT");
        assertThat(suggestions.get(0).purchaseTransactionId()).isEqualTo(1L);
    }

    @Test
    void reportsAmountMatchWhenTheRefundCarriesNoAnchor() {
        Transaction purchase = expense(1L, 1L, "-12.00", "2026-06-20", "SOME SHOP", null);
        Transaction refund = income(2L, 1L, "12.00", "2026-06-25",
                REVERSAL + "Nr ref.: 0000000000000000");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        List<SuggestedRefund> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).reason()).isEqualTo("AMOUNT");
    }

    @Test
    void ignoresATaxOfficeRefund() {
        Transaction purchase = expense(1L, 1L, "-2500.00", "2026-07-01", "EXAMPLE MERCHANT", null);
        Transaction refund = income(2L, 1L, "2500.00", "2026-07-09", TAX_REFUND);
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        assertThat(service.suggest()).isEmpty();
    }

    @Test
    void neverPicksAPurchaseDatedAfterTheRefund() {
        Transaction purchase = expense(1L, 1L, "-50.00", "2026-06-03", "SOME SHOP", null);
        Transaction refund = income(2L, 1L, "50.00", "2026-06-01",
                REVERSAL + "WYKONANEJ DN. 03/06/2026");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        assertThat(service.suggest()).isEmpty();
    }

    @Test
    void neverPairsAcrossAccounts() {
        Transaction purchase = expense(1L, 2L, "-50.00", "2026-06-01", "SOME SHOP", null);
        Transaction refund = income(2L, 1L, "50.00", "2026-06-01",
                REVERSAL + "WYKONANEJ DN. 01/06/2026");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        assertThat(service.suggest()).isEmpty();
    }

    @Test
    void neverPairsAPurchaseOlderThanTheSearchWindow() {
        Transaction purchase = expense(1L, 1L, "-50.00", "2026-01-01", "SOME SHOP", null);
        Transaction refund = income(2L, 1L, "50.00", "2026-06-01",
                REVERSAL + "WYKONANEJ DN. 01/01/2026");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, refund));

        assertThat(service.suggest()).isEmpty();
    }

    @Test
    void spendsEachPurchaseOnASingleRefund() {
        Transaction purchase = expense(1L, 1L, "-50.00", "2026-06-01", "SOME SHOP", null);
        Transaction first = income(2L, 1L, "50.00", "2026-06-01", REVERSAL + "Nr ref.: 1");
        Transaction second = income(3L, 1L, "50.00", "2026-06-02", REVERSAL + "Nr ref.: 2");
        when(transactions.findAllStatistical()).thenReturn(List.of(purchase, first, second));

        List<SuggestedRefund> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).refundTransactionId()).isEqualTo(2L);
    }

    @Test
    void applySkipsThePairsThatCanNoLongerBeLinked() {
        Transaction firstPurchase = expense(1L, 1L, "-50.00", "2026-06-01", "SOME SHOP", null);
        Transaction secondPurchase = expense(2L, 1L, "-70.00", "2026-06-01", "OTHER SHOP", null);
        Transaction firstRefund = income(3L, 1L, "50.00", "2026-06-01", "ZA ZWROT TRANSAKCJI Nr ref.: 1");
        Transaction secondRefund = income(4L, 1L, "70.00", "2026-06-01", "ZA ZWROT TRANSAKCJI Nr ref.: 2");
        when(transactions.findAllStatistical())
                .thenReturn(List.of(firstPurchase, secondPurchase, firstRefund, secondRefund));
        when(edit.pairRefund(1L, 3L)).thenThrow(new IllegalArgumentException("already paired"));
        when(edit.pairRefund(2L, 4L)).thenReturn(List.of());

        assertThat(service.apply(edit)).isEqualTo(1);
        verify(edit).pairRefund(2L, 4L);
    }

    private static Transaction expense(long id, long accountId, String amount, String date,
                                       String merchant, String description) {
        return transaction(id, accountId, amount, date, merchant, description);
    }

    private static Transaction income(long id, long accountId, String amount, String date, String description) {
        return transaction(id, accountId, amount, date, null, description);
    }

    private static Transaction transaction(long id, long accountId, String amount, String date,
                                           String merchant, String description) {
        BigDecimal value = new BigDecimal(amount);
        return Transaction.builder()
                .id(id)
                .statementId(1L)
                .accountId(accountId)
                .transactionDate(LocalDate.parse(date))
                .amount(value)
                .currency("PLN")
                .nature(TransactionNature.forSignedAmount(value))
                .merchant(merchant)
                .description(description)
                .build();
    }
}
