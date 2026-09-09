package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementParser;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.FxRateProvider;
import com.financedashboard.domain.port.MerchantRuleRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.ParsedStatement;
import com.financedashboard.domain.statement.ParsedTransaction;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    private StatementImportService service;

    @BeforeEach
    void setUp() {
        service = new StatementImportService(
                List.of(parser), statements, transactions, accounts, fxRates, merchantRules);
        ReflectionTestUtils.setField(service, "baseCurrency", "PLN");
    }

    @Test
    void importTagsFreshRowsWhoseMerchantMatchesARule() {
        when(parser.canParse(any())).thenReturn(true);
        when(parser.parse(any())).thenReturn(new ParsedStatement(
                "PEKAO", "PLN",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                List.of(new ParsedTransaction(LocalDate.of(2026, 3, 5), BigDecimal.valueOf(-250.50),
                        "Podatek", "Example Merchant"))));
        when(accounts.findById(1L)).thenReturn(Optional.of(
                Account.builder().id(1L).name("A").currency("PLN").build()));
        when(statements.existsByFileHash(anyString())).thenReturn(false);
        when(statements.save(any())).thenReturn(
                BankStatement.builder().id(10L).accountId(1L).bank("PEKAO").build());
        when(transactions.findExistingDedupHashes(any(), any())).thenReturn(Set.of());
        when(merchantRules.findAll()).thenReturn(List.of(
                MerchantRule.builder().merchant("EXAMPLE MERCHANT").categoryId(7L).build()));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.importStatement(new byte[] {1}, null, 1L, "wyciag.pdf");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        Transaction saved = captor.getValue().get(0);
        assertThat(saved.getCategoryId()).isEqualTo(7L);
        assertThat(saved.getNature()).isEqualTo(TransactionNature.EXPENSE);
        assertThat(saved.getMerchant()).isEqualTo("Example Merchant");
    }
}
