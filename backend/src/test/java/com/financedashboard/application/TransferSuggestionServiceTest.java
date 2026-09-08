package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.financedashboard.application.TransferSuggestionService.SuggestedTransfer;
import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferSuggestionServiceTest {

    private static final String ACCOUNT_A = "00000000000000000000000003";
    private static final String ACCOUNT_B = "00000000000000000000000004";

    @Mock
    private AccountRepository accounts;
    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private TransferSuggestionService service;

    private Account accountA;
    private Account accountB;

    @BeforeEach
    void accounts() {
        accountA = account(1L, ACCOUNT_A);
        accountB = account(2L, ACCOUNT_B);
        when(accounts.findAll()).thenReturn(List.of(accountA, accountB));
    }

    @Test
    void pairsMirroredLegsAcrossCurrencies() {
        Transaction out = tx(10L, 1L, "-100.00", "USD", "PRZELEW MOBILE BENF " + ACCOUNT_B);
        Transaction in = tx(11L, 2L, "50.00", "PLN", "PRZELEW MOBILE SACC " + ACCOUNT_A);
        when(transactions.findAllNonTransfers()).thenReturn(List.of(out, in));

        List<SuggestedTransfer> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).fromTransactionId()).isEqualTo(10L);
        assertThat(suggestions.get(0).toTransactionId()).isEqualTo(11L);
        assertThat(suggestions.get(0).reason()).isEqualTo("MIRROR");
    }

    @Test
    void pairsEqualAmountInboundAsFallback() {
        Transaction out = tx(20L, 1L, "-100.00", "USD", "PRZELEW MOBILE to " + ACCOUNT_B);
        Transaction in = tx(21L, 2L, "100.00", "USD", "some income");
        when(transactions.findAllNonTransfers()).thenReturn(List.of(out, in));

        List<SuggestedTransfer> suggestions = service.suggest();

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).reason()).isEqualTo("AMOUNT");
    }

    @Test
    void ignoresOutboundNotReferencingAnotherOwnAccount() {
        Transaction out = tx(30L, 1L, "-100.00", "USD", "EXAMPLE STORE.COM 99999999");
        Transaction in = tx(31L, 2L, "100.00", "USD", "some income");
        when(transactions.findAllNonTransfers()).thenReturn(List.of(out, in));

        assertThat(service.suggest()).isEmpty();
    }

    private static Account account(long id, String accountNumber) {
        return Account.builder().id(id).name("acct").currency("USD")
                .accountNumber(accountNumber).sortOrder(0).build();
    }

    private static Transaction tx(long id, long accountId, String amount, String currency, String description) {
        return Transaction.builder()
                .id(id)
                .statementId(1L)
                .accountId(accountId)
                .transactionDate(LocalDate.of(2026, 3, 10))
                .amount(new BigDecimal(amount))
                .currency(currency)
                .nature(TransactionNature.forSignedAmount(new BigDecimal(amount)))
                .description(description)
                .build();
    }
}
