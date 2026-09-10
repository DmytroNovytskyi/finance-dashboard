package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.StatementService.StatementSummary;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.StatementOrder;
import com.financedashboard.domain.statement.StatementSortField;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private BankStatementRepository statements;
    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private StatementService service;

    private static BankStatement statement(Long id) {
        return BankStatement.builder()
                .id(id)
                .accountId(1L)
                .bank("PEKAO")
                .periodStart(LocalDate.of(2026, 8, 1))
                .periodEnd(LocalDate.of(2026, 8, 31))
                .fileName("wyciag.pdf")
                .fileHash("hash-" + id)
                .importedAt(Instant.parse("2026-09-01T10:00:00Z"))
                .build();
    }

    @Test
    void listsStatementsInTheRequestedOrderWithTheirCounts() {
        BankStatement older = statement(1L);
        BankStatement newer = statement(2L);
        StatementOrder order = new StatementOrder(StatementSortField.IMPORTED, false);
        when(statements.findAll(order, null)).thenReturn(List.of(newer, older));
        when(transactions.countByStatementIds(List.of(2L, 1L))).thenReturn(Map.of(2L, 5L));

        List<StatementSummary> summaries = service.list(order, null);

        assertThat(summaries).extracting(StatementSummary::statement).containsExactly(newer, older);
        assertThat(summaries.get(0).transactionCount()).isEqualTo(5L);
        assertThat(summaries.get(1).transactionCount()).isZero();
    }

    @Test
    void restrictsTheListToOneAccountWhenAsked() {
        StatementOrder order = new StatementOrder(StatementSortField.PERIOD, true);
        when(statements.findAll(order, 7L)).thenReturn(List.of(statement(1L)));
        when(transactions.countByStatementIds(List.of(1L))).thenReturn(Map.of());

        assertThat(service.list(order, 7L)).hasSize(1);

        verify(statements).findAll(order, 7L);
    }

    @Test
    void returnsEmptyWhenNoStatementsImported() {
        StatementOrder order = new StatementOrder(StatementSortField.IMPORTED, false);
        when(statements.findAll(order, null)).thenReturn(List.of());

        assertThat(service.list(order, null)).isEmpty();

        verify(transactions, never()).countByStatementIds(org.mockito.ArgumentMatchers.any());
    }
}
