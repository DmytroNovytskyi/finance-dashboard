package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionEditServiceTest {

    @Mock
    private TransactionRepository transactions;
    @Mock
    private CategoryRepository categories;
    @Mock
    private BankStatementRepository statements;

    @InjectMocks
    private TransactionEditService service;

    private static Transaction transaction(Long id, Long accountId, BigDecimal amount, TransactionNature nature) {
        return Transaction.builder()
                .id(id)
                .statementId(1L)
                .accountId(accountId)
                .transactionDate(LocalDate.of(2026, 8, 1))
                .amount(amount)
                .currency("PLN")
                .nature(nature)
                .build();
    }

    @Test
    void categorizeAssignsCategory() {
        Transaction current = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(current));
        when(categories.existsById(5L)).thenReturn(true);
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction updated = service.categorize(1L, true, 5L, null);

        assertThat(updated.getCategoryId()).isEqualTo(5L);
        assertThat(updated.getNature()).isEqualTo(TransactionNature.EXPENSE);
    }

    @Test
    void categorizeClearsCategoryWhenExplicitlyNull() {
        Transaction current = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        Transaction withCategory = current.toBuilder().categoryId(5L).build();
        when(transactions.findById(1L)).thenReturn(Optional.of(withCategory));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction updated = service.categorize(1L, true, null, null);

        assertThat(updated.getCategoryId()).isNull();
    }

    @Test
    void categorizeRejectsTransferNature() {
        assertThatThrownBy(() -> service.categorize(1L, false, null, TransactionNature.TRANSFER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categorizeBulkRejectsUnknownCategory() {
        when(categories.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.categorizeBulk(java.util.List.of(1L), 99L))
                .isInstanceOf(com.financedashboard.application.exception.NotFoundException.class);
        verify(transactions, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void pairTransferRejectsSameAccount() {
        Transaction first = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        Transaction second = transaction(2L, 1L, new BigDecimal("10"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(first));
        when(transactions.findById(2L)).thenReturn(Optional.of(second));

        assertThatThrownBy(() -> service.pairTransfer(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pairTransferMarksBothLegsWithSharedGroup() {
        Transaction first = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        Transaction second = transaction(2L, 2L, new BigDecimal("8"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(first));
        when(transactions.findById(2L)).thenReturn(Optional.of(second));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var legs = service.pairTransfer(1L, 2L);

        assertThat(legs).hasSize(2);
        assertThat(legs).allSatisfy(leg -> assertThat(leg.getNature()).isEqualTo(TransactionNature.TRANSFER));
        assertThat(legs.get(0).getTransferGroupId()).isEqualTo(legs.get(1).getTransferGroupId()).isNotNull();
    }
}
