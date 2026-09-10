package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.StatementCoverage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementCoverageServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BankStatementRepository statements;
    @Mock
    private AccountRepository accounts;

    private StatementCoverageService service;

    @BeforeEach
    void setUp() {
        service = new StatementCoverageService(statements, accounts, FIXED_CLOCK);
    }

    private static Account account(Long id) {
        return Account.builder().id(id).name("Account " + id).currency("PLN").sortOrder(0).build();
    }

    private static BankStatement statement(Long accountId, LocalDate start, LocalDate end) {
        return BankStatement.builder()
                .accountId(accountId)
                .bank("PEKAO")
                .periodStart(start)
                .periodEnd(end)
                .fileName("wyciag.pdf")
                .fileHash("hash-" + accountId + start)
                .importedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
    }

    @Test
    void reportsCoveragePerAccountMeasuredAgainstTheClockTodaysDate() {
        when(accounts.findAll()).thenReturn(List.of(account(1L), account(2L)));
        when(statements.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(
                statement(1L, LocalDate.of(2026, 6, 27), LocalDate.of(2026, 7, 25)),
                statement(2L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))));

        List<StatementCoverage> coverage = service.coverage();

        assertThat(coverage).extracting(StatementCoverage::accountId).containsExactly(1L, 2L);
        assertThat(coverage.get(0).latestPeriodEnd()).isEqualTo(LocalDate.of(2026, 7, 25));
        assertThat(coverage.get(0).missingPeriodEnds()).containsExactly(LocalDate.of(2026, 8, 25));
        assertThat(coverage.get(1).missingPeriodEnds()).isEmpty();
    }

    @Test
    void assignsEachStatementToItsOwnAccount() {
        when(accounts.findAll()).thenReturn(List.of(account(1L), account(2L)));
        when(statements.findAllByOrderByPeriodStartAsc()).thenReturn(List.of(
                statement(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                statement(2L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                statement(1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))));

        List<StatementCoverage> coverage = service.coverage();

        assertThat(coverage.get(0).statementCount()).isEqualTo(2);
        assertThat(coverage.get(1).statementCount()).isEqualTo(1);
    }

    @Test
    void includesAnAccountThatHasNeverHadAStatement() {
        when(accounts.findAll()).thenReturn(List.of(account(9L)));
        when(statements.findAllByOrderByPeriodStartAsc()).thenReturn(List.of());

        List<StatementCoverage> coverage = service.coverage();

        assertThat(coverage).hasSize(1);
        assertThat(coverage.get(0).accountId()).isEqualTo(9L);
        assertThat(coverage.get(0).statementCount()).isZero();
        assertThat(coverage.get(0).earliestPeriodStart()).isNull();
        assertThat(coverage.get(0).missingPeriodEnds()).isEmpty();
    }
}
