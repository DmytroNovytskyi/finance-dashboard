package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementParser;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.FxRateProvider;
import com.financedashboard.domain.port.MerchantRuleRepository;
import com.financedashboard.domain.port.TransactionAmountRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.statement.ParsedTransaction;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionAmount;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock
    private BankStatementParser parser;
    @Mock
    private BankStatementRepository statements;
    @Mock
    private TransactionRepository transactions;
    @Mock
    private AccountRepository accounts;
    @Mock
    private FxRateProvider fxRates;
    @Mock
    private MerchantRuleRepository merchantRules;
    @Mock
    private TransferSuggestionService transferSuggestions;
    @Mock
    private TransactionEditService transactionEdit;
    @Mock
    private TransactionAmountRepository transactionAmounts;

    private StatementImportService service;

    @BeforeEach
    void setUp() {
        service = new StatementImportService(
                List.of(parser), statements, transactions, accounts, fxRates, merchantRules,
                transferSuggestions, transactionEdit, transactionAmounts,
                new SupportedCurrencies("PLN", List.of("PLN")));
    }

    private ParsedStatement parsed(String accountNumber) {
        return new ParsedStatement(
                "PEKAO", "PLN",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(new ParsedTransaction(LocalDate.of(2026, 3, 5), BigDecimal.valueOf(-250.50),
                        "Podatek", "Example Merchant")),
                accountNumber);
    }

    private void stubParserAndStorage(long accountId) {
        when(parser.canParse(any())).thenReturn(true);
        when(statements.existsByFileHash(anyString())).thenReturn(false);
        when(statements.save(any())).thenReturn(
                BankStatement.builder().id(10L).accountId(accountId).bank("PEKAO").build());
        when(transactions.findExistingDedupHashes(any(), any())).thenReturn(Set.of());
        when(merchantRules.findAll()).thenReturn(List.of());
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void importReusesTheStoredAccountWhoseNumberMatchesTheStatement() {
        Account existing = Account.builder()
                .id(1L).name("Pekao PLN").currency("PLN")
                .accountNumber("00000000000000000000000002").build();
        when(parser.parse(any())).thenReturn(parsed("00000000000000000000000002"));
        when(accounts.findByAccountNumber("00000000000000000000000002"))
                .thenReturn(Optional.of(existing));
        stubParserAndStorage(1L);

        service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        verify(accounts, never()).save(any());
        ArgumentCaptor<BankStatement> captor = ArgumentCaptor.forClass(BankStatement.class);
        verify(statements).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(1L);
    }

    @Test
    void importCreatesAnAccountWhenTheStatementNamesAnUnknownNumber() {
        when(parser.parse(any())).thenReturn(parsed("999900001111000000002222"));
        when(accounts.findByAccountNumber("999900001111000000002222")).thenReturn(Optional.empty());
        when(accounts.save(any())).thenAnswer(invocation ->
                ((Account) invocation.getArgument(0)).toBuilder().id(7L).build());
        stubParserAndStorage(7L);

        service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(captor.capture());
        Account created = captor.getValue();
        assertThat(created.getCurrency()).isEqualTo("PLN");
        assertThat(created.getAccountNumber()).isEqualTo("999900001111000000002222");
        assertThat(created.getName()).contains("999900001111000000002222".substring(22));
    }

    @Test
    void importReusesTheSoleAccountOfTheCurrencyWhenNoNumberIsStated() {
        when(parser.parse(any())).thenReturn(parsed(null));
        when(accounts.findFirstByCurrency("PLN")).thenReturn(Optional.of(
                Account.builder().id(3L).name("Pekao PLN").currency("PLN").build()));
        stubParserAndStorage(3L);

        service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        verify(accounts, never()).save(any());
        ArgumentCaptor<BankStatement> captor = ArgumentCaptor.forClass(BankStatement.class);
        verify(statements).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(3L);
    }

    @Test
    void importRejectsAStatementNumberPointingToAnotherCurrencyAccount() {
        when(parser.canParse(any())).thenReturn(true);
        when(parser.parse(any())).thenReturn(parsed("00000000000000000000000002"));
        when(accounts.findByAccountNumber("00000000000000000000000002")).thenReturn(Optional.of(
                Account.builder().id(1L).name("USD account").currency("USD").build()));

        assertThatThrownBy(() -> service.importStatement(new byte[] {1}, null, "wyciag.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importTagsFreshRowsWhoseMerchantMatchesARule() {
        when(parser.parse(any())).thenReturn(parsed("00000000000000000000000002"));
        when(accounts.findByAccountNumber("00000000000000000000000002")).thenReturn(Optional.of(
                Account.builder().id(1L).name("Pekao PLN").currency("PLN").build()));
        stubParserAndStorage(1L);
        when(merchantRules.findAll()).thenReturn(List.of(
                MerchantRule.builder().merchant("EXAMPLE MERCHANT").categoryId(7L).build()));

        service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        Transaction saved = captor.getValue().get(0);
        assertThat(saved.getCategoryId()).isEqualTo(7L);
        assertThat(saved.getNature()).isEqualTo(TransactionNature.EXPENSE);
        assertThat(saved.getMerchant()).isEqualTo("Example Merchant");
    }

    @Test
    void importStoresTheNativeCurrencyChildValueForEveryFreshRow() {
        when(parser.parse(any())).thenReturn(parsed("00000000000000000000000002"));
        when(accounts.findByAccountNumber("00000000000000000000000002")).thenReturn(Optional.of(
                Account.builder().id(1L).name("Pekao PLN").currency("PLN").build()));
        stubParserAndStorage(1L);

        service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TransactionAmount>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionAmounts).saveAll(captor.capture());
        TransactionAmount child = captor.getValue().get(0);
        assertThat(child.currency()).isEqualTo("PLN");
        assertThat(child.amount()).isEqualByComparingTo("-250.50");
    }

    @Test
    void duplicateFileReturnsAlreadyImportedWithoutResolvingAnAccount() {
        when(parser.canParse(any())).thenReturn(true);
        when(statements.existsByFileHash(anyString())).thenReturn(true);

        StatementImportService.StatementImportResult result =
                service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        assertThat(result.alreadyImported()).isTrue();
        verify(accounts, never()).findByAccountNumber(any());
        verify(statements, never()).save(any());
        verify(transactionAmounts, never()).saveAll(any());
        verify(transferSuggestions, never()).autoPairForImported(any(), any());
    }

    @Test
    void importAutoPairsFreshRowsAgainstStoredMirrors() {
        when(parser.parse(any())).thenReturn(parsed("00000000000000000000000002"));
        when(accounts.findByAccountNumber("00000000000000000000000002")).thenReturn(Optional.of(
                Account.builder().id(1L).name("Pekao PLN").currency("PLN").build()));
        stubParserAndStorage(1L);
        when(transactions.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Transaction> rows = invocation.getArgument(0);
            return IntStream.range(0, rows.size())
                    .mapToObj(i -> rows.get(i).toBuilder().id(100L + i).build())
                    .toList();
        });

        service.importStatement(new byte[] {1}, null, "wyciag.pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferSuggestions).autoPairForImported(captor.capture(), eq(transactionEdit));
        assertThat(captor.getValue()).containsExactly(100L);
    }
}
