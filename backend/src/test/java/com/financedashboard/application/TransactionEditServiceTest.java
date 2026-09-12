package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    @Mock
    private AccountRepository accounts;

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

    private static Transaction leg(Long id, Long statementId, UUID group) {
        return Transaction.builder()
                .id(id)
                .statementId(statementId)
                .accountId(statementId)
                .transactionDate(LocalDate.of(2026, 8, 1))
                .amount(new BigDecimal("-10"))
                .currency("PLN")
                .nature(TransactionNature.TRANSFER)
                .transferGroupId(group)
                .build();
    }

    private static BankStatement statement(Long id) {
        return BankStatement.builder().id(id).accountId(id).bank("PEKAO").fileHash("h-" + id).build();
    }

    private static Category category(Long id, String name) {
        return Category.builder().id(id).name(name).build();
    }

    private static Category reservedCategory(Long id, String name, String systemKey) {
        return Category.builder().id(id).name(name).system(true).systemKey(systemKey).build();
    }

    @Test
    void categorizeAssignsCategory() {
        Transaction current = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(current));
        when(categories.findById(5L)).thenReturn(Optional.of(category(5L, "Groceries")));
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
    void categorizeRejectsAReservedCategory() {
        Transaction current = transaction(1L, 1L, new BigDecimal("-49.99"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(current));
        when(categories.findById(13L))
                .thenReturn(Optional.of(reservedCategory(13L, "Refund", Category.SYSTEM_KEY_REFUND)));

        assertThatThrownBy(() -> service.categorize(1L, true, 13L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void categorizeRejectsACategoryChangeOnALinkedRow() {
        Transaction leg = transaction(1L, 1L, new BigDecimal("-49.99"), TransactionNature.REFUND)
                .toBuilder().refundGroupId(UUID.randomUUID()).categoryId(13L).build();
        when(transactions.findById(1L)).thenReturn(Optional.of(leg));

        assertThatThrownBy(() -> service.categorize(1L, true, 5L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlink");
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void categorizeRejectsANatureChangeOnALinkedRow() {
        Transaction leg = transaction(1L, 1L, new BigDecimal("-49.99"), TransactionNature.REFUND)
                .toBuilder().refundGroupId(UUID.randomUUID()).categoryId(13L).build();
        when(transactions.findById(1L)).thenReturn(Optional.of(leg));

        assertThatThrownBy(() -> service.categorize(1L, false, null, TransactionNature.EXPENSE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlink");
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void categorizeBulkRejectsUnknownCategory() {
        when(categories.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.categorizeBulk(java.util.List.of(1L), 99L))
                .isInstanceOf(com.financedashboard.application.exception.NotFoundException.class);
        verify(transactions, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void categorizeBulkRejectsAReservedCategory() {
        when(categories.findById(6L)).thenReturn(
                Optional.of(reservedCategory(6L, "Internal Transfer", Category.SYSTEM_KEY_TRANSFER)));

        assertThatThrownBy(() -> service.categorizeBulk(java.util.List.of(1L), 6L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(transactions, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void categorizeBulkRejectsALinkedRowInTheSelection() {
        Transaction leg = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.TRANSFER)
                .toBuilder().transferGroupId(UUID.randomUUID()).categoryId(6L).build();
        when(categories.findById(5L)).thenReturn(Optional.of(category(5L, "Groceries")));
        when(transactions.findById(1L)).thenReturn(Optional.of(leg));

        assertThatThrownBy(() -> service.categorizeBulk(java.util.List.of(1L), 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unlink");
        verify(transactions, org.mockito.Mockito.never()).save(any());
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

    @Test
    void deleteStatementRemovesItsRowsTheStatementAndAnAccountLeftEmpty() {
        BankStatement statement = statement(2L);
        Transaction row = transaction(7L, 2L, new BigDecimal("-10"), TransactionNature.EXPENSE)
                .toBuilder().statementId(2L).build();
        when(statements.findById(2L)).thenReturn(Optional.of(statement));
        when(transactions.findByStatementId(2L)).thenReturn(List.of(row));

        int deleted = service.deleteStatement(2L);

        assertThat(deleted).isEqualTo(1);
        verify(transactions).deleteAll(List.of(row));
        verify(statements).deleteById(2L);
        verify(accounts).deleteById(2L);
    }

    @Test
    void deleteStatementKeepsAnAccountThatStillHoldsTransactions() {
        BankStatement statement = statement(2L);
        Transaction row = transaction(7L, 2L, new BigDecimal("-10"), TransactionNature.EXPENSE)
                .toBuilder().statementId(2L).build();
        when(statements.findById(2L)).thenReturn(Optional.of(statement));
        when(transactions.findByStatementId(2L)).thenReturn(List.of(row));
        when(transactions.existsByAccountId(2L)).thenReturn(true);

        service.deleteStatement(2L);

        verify(accounts, org.mockito.Mockito.never()).deleteById(2L);
    }

    @Test
    void deleteStatementUnpairsSurvivingTransferLeg() {
        BankStatement statement = statement(2L);
        UUID group = UUID.randomUUID();
        Transaction doomedLeg = leg(7L, 2L, group);
        Transaction survivingLeg = leg(8L, 3L, group);
        when(statements.findById(2L)).thenReturn(Optional.of(statement));
        when(transactions.findByStatementId(2L)).thenReturn(List.of(doomedLeg));
        when(transactions.findByTransferGroupIds(List.of(group))).thenReturn(List.of(doomedLeg, survivingLeg));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteStatement(2L);

        org.mockito.ArgumentCaptor<Transaction> captor =
                org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(captor.capture());
        Transaction unpairCall = captor.getValue();
        assertThat(unpairCall.getTransferGroupId()).isNull();
        assertThat(unpairCall.getNature()).isEqualTo(TransactionNature.EXPENSE);
    }

    @Test
    void deleteStatementRejectsUnknownStatement() {
        when(statements.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteStatement(99L))
                .isInstanceOf(com.financedashboard.application.exception.NotFoundException.class);

        verify(transactions, org.mockito.Mockito.never()).findByStatementId(any());
    }

    @Test
    void deleteRangeRemovesAccountLeftWithoutStatementsOrTransactions() {
        Transaction doomed = transaction(1L, 5L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findByDateRangeAndAccount(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null))
                .thenReturn(List.of(doomed));

        int deleted = service.deleteRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), null);

        assertThat(deleted).isEqualTo(1);
        verify(transactions).deleteAll(List.of(doomed));
        verify(accounts).deleteById(5L);
    }

    @Test
    void uncategorizeAllClearsEveryCategorizedRow() {
        Transaction categorized = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE)
                .toBuilder().categoryId(5L).build();
        when(transactions.findCategorized()).thenReturn(List.of(categorized));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int cleared = service.uncategorizeAll();

        assertThat(cleared).isEqualTo(1);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Transaction>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getCategoryId()).isNull();
    }

    @Test
    void uncategorizeByCategoryClearsOnlyRowsOfThatCategory() {
        Transaction inTarget = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE)
                .toBuilder().categoryId(5L).build();
        Transaction other = transaction(2L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE)
                .toBuilder().categoryId(9L).build();
        when(transactions.findCategorized()).thenReturn(List.of(inTarget, other));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categories.findById(5L)).thenReturn(Optional.of(category(5L, "Groceries")));

        int cleared = service.uncategorizeByCategory(5L);

        assertThat(cleared).isEqualTo(1);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Transaction>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Transaction::getId).containsExactly(1L);
    }

    @Test
    void uncategorizeByCategoryRejectsAReservedCategory() {
        when(categories.findById(6L)).thenReturn(
                Optional.of(reservedCategory(6L, "Internal Transfer", Category.SYSTEM_KEY_TRANSFER)));

        assertThatThrownBy(() -> service.uncategorizeByCategory(6L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(transactions, org.mockito.Mockito.never()).findCategorized();
    }

    @Test
    void deleteAccountRemovesItsTransactionsStatementsAndTheAccount() {
        when(accounts.findById(5L)).thenReturn(Optional.of(
                com.financedashboard.domain.account.Account.builder().id(5L).name("A").currency("PLN").build()));
        Transaction row = transaction(1L, 5L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findByAccountId(5L)).thenReturn(List.of(row));
        BankStatement owned = BankStatement.builder().id(2L).accountId(5L).bank("PEKAO").build();
        when(statements.findByAccountId(5L)).thenReturn(List.of(owned));

        int deleted = service.deleteAccountAndTransactions(5L);

        assertThat(deleted).isEqualTo(1);
        verify(transactions).deleteAll(List.of(row));
        verify(statements).deleteById(2L);
        verify(accounts).deleteById(5L);
    }

    @Test
    void deleteAccountRejectsUnknownAccount() {
        when(accounts.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAccountAndTransactions(5L))
                .isInstanceOf(com.financedashboard.application.exception.NotFoundException.class);
        verify(accounts, org.mockito.Mockito.never()).deleteById(any());
    }

    @Test
    void deleteRangeKeepsAccountStillReferencedByAStatement() {
        Transaction doomed = transaction(1L, 5L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findByDateRangeAndAccount(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L))
                .thenReturn(List.of(doomed));
        when(statements.existsByAccountId(5L)).thenReturn(true);

        service.deleteRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 5L);

        verify(accounts, org.mockito.Mockito.never()).deleteById(5L);
    }

    @Test
    void pairIfBothUncategorizedLeavesACategorizedLegAlone() {
        Transaction categorized = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE)
                .toBuilder().categoryId(5L).build();
        when(transactions.findById(1L)).thenReturn(Optional.of(categorized));

        boolean paired = service.pairIfBothUncategorized(1L, 2L);

        assertThat(paired).isFalse();
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void pairIfBothUncategorizedPairsWhenNeitherLegHasACategory() {
        Transaction first = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        Transaction second = transaction(2L, 2L, new BigDecimal("8"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(first));
        when(transactions.findById(2L)).thenReturn(Optional.of(second));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean paired = service.pairIfBothUncategorized(1L, 2L);

        assertThat(paired).isTrue();
        verify(transactions, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void unpairTransferRevertsBothLegsToTheirNaturalNature() {
        UUID group = UUID.randomUUID();
        Transaction negative = leg(1L, 1L, group).toBuilder().categoryId(9L).build();
        Transaction positive = leg(2L, 2L, group).toBuilder()
                .amount(new BigDecimal("10"))
                .nature(TransactionNature.TRANSFER)
                .categoryId(9L)
                .build();
        when(transactions.findById(1L)).thenReturn(Optional.of(negative));
        when(transactions.findByTransferGroupIds(List.of(group))).thenReturn(List.of(negative, positive));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Transaction> reverted = service.unpairTransfer(1L);

        assertThat(reverted).hasSize(2);
        assertThat(reverted).allSatisfy(t -> {
            assertThat(t.getNature()).isEqualTo(TransactionNature.forSignedAmount(t.getAmount()));
            assertThat(t.getTransferGroupId()).isNull();
            assertThat(t.getCategoryId()).isNull();
        });
    }

    @Test
    void unpairTransferRejectsANonTransferRow() {
        Transaction plain = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(plain));

        assertThatThrownBy(() -> service.unpairTransfer(1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void pairRefundMarksBothLegsWithTheSharedGroupAndTheReservedCategory() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.EXPENSE);
        Transaction refund = transaction(2L, 1L, new BigDecimal("17.80"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(refund));
        when(categories.findSystemCategory("REFUND"))
                .thenReturn(Optional.of(Category.builder().id(9L).name("Refund").build()));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var legs = service.pairRefund(1L, List.of(2L));

        assertThat(legs).allSatisfy(leg -> {
            assertThat(leg.getNature()).isEqualTo(TransactionNature.REFUND);
            assertThat(leg.getCategoryId()).isEqualTo(9L);
        });
        assertThat(legs.get(0).getRefundGroupId()).isEqualTo(legs.get(1).getRefundGroupId()).isNotNull();
    }

    @Test
    void pairRefundGroupsOnePurchaseWithSeveralCredits() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-61.46"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(
                transaction(2L, 1L, new BigDecimal("14.45"), TransactionNature.INCOME)));
        when(transactions.findById(3L)).thenReturn(Optional.of(
                transaction(3L, 1L, new BigDecimal("23.27"), TransactionNature.INCOME)));
        when(transactions.findById(4L)).thenReturn(Optional.of(
                transaction(4L, 1L, new BigDecimal("23.74"), TransactionNature.INCOME)));
        when(categories.findSystemCategory("REFUND"))
                .thenReturn(Optional.of(Category.builder().id(9L).name("Refund").build()));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var legs = service.pairRefund(1L, List.of(2L, 3L, 4L));

        assertThat(legs).hasSize(4);
        assertThat(legs).allSatisfy(leg -> {
            assertThat(leg.getNature()).isEqualTo(TransactionNature.REFUND);
            assertThat(leg.getCategoryId()).isEqualTo(9L);
        });
        assertThat(legs.get(0).getId()).isEqualTo(1L);
        assertThat(legs.stream().map(Transaction::getRefundGroupId).distinct()).hasSize(1);
    }

    @Test
    void pairRefundRejectsCreditsThatDoNotAddUpToThePurchase() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-61.46"), TransactionNature.EXPENSE);
        Transaction first = transaction(2L, 1L, new BigDecimal("14.45"), TransactionNature.INCOME);
        Transaction second = transaction(3L, 1L, new BigDecimal("23.27"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(first));
        when(transactions.findById(3L)).thenReturn(Optional.of(second));

        assertThatThrownBy(() -> service.pairRefund(1L, List.of(2L, 3L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void pairRefundRejectsAnEmptyCreditList() {
        assertThatThrownBy(() -> service.pairRefund(1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void pairRefundRejectsAPurchaseThatCameIn() {
        Transaction incoming = transaction(1L, 1L, new BigDecimal("17.80"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(incoming));

        assertThatThrownBy(() -> service.pairRefund(1L, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pairRefundRejectsLegsInDifferentAccounts() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.EXPENSE);
        Transaction refund = transaction(2L, 2L, new BigDecimal("17.80"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.pairRefund(1L, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pairRefundRejectsLegsOfTheSameSign() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.EXPENSE);
        Transaction other = transaction(2L, 1L, new BigDecimal("-17.80"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.pairRefund(1L, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pairRefundRejectsAPartialRefund() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.EXPENSE);
        Transaction partial = transaction(2L, 1L, new BigDecimal("8.90"), TransactionNature.INCOME);
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(partial));

        assertThatThrownBy(() -> service.pairRefund(1L, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pairRefundRejectsALegThatIsAlreadyPaired() {
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.EXPENSE);
        Transaction refund = transaction(2L, 1L, new BigDecimal("17.80"), TransactionNature.INCOME)
                .toBuilder().refundGroupId(UUID.randomUUID()).build();
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findById(2L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.pairRefund(1L, List.of(2L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void unpairRefundRevertsBothLegsToTheirNaturalNature() {
        UUID group = UUID.randomUUID();
        Transaction purchase = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.REFUND)
                .toBuilder().refundGroupId(group).categoryId(9L).build();
        Transaction refund = transaction(2L, 1L, new BigDecimal("17.80"), TransactionNature.REFUND)
                .toBuilder().refundGroupId(group).categoryId(9L).build();
        when(transactions.findById(1L)).thenReturn(Optional.of(purchase));
        when(transactions.findByRefundGroupIds(List.of(group))).thenReturn(List.of(purchase, refund));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Transaction> reverted = service.unpairRefund(1L);

        assertThat(reverted).hasSize(2);
        assertThat(reverted).allSatisfy(leg -> {
            assertThat(leg.getNature()).isEqualTo(TransactionNature.forSignedAmount(leg.getAmount()));
            assertThat(leg.getRefundGroupId()).isNull();
            assertThat(leg.getCategoryId()).isNull();
        });
    }

    @Test
    void unpairRefundRejectsARowThatIsNotPartOfARefund() {
        Transaction plain = transaction(1L, 1L, new BigDecimal("-10"), TransactionNature.EXPENSE);
        when(transactions.findById(1L)).thenReturn(Optional.of(plain));

        assertThatThrownBy(() -> service.unpairRefund(1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(transactions, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void categorizeRejectsRefundNature() {
        assertThatThrownBy(() -> service.categorize(1L, false, null, TransactionNature.REFUND))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRangeUnpairsTheRefundLegItLeavesBehind() {
        UUID group = UUID.randomUUID();
        Transaction doomed = transaction(1L, 1L, new BigDecimal("-17.80"), TransactionNature.REFUND)
                .toBuilder().refundGroupId(group).categoryId(9L).build();
        Transaction survivor = transaction(2L, 1L, new BigDecimal("17.80"), TransactionNature.REFUND)
                .toBuilder().refundGroupId(group).categoryId(9L).build();
        when(transactions.findByDateRangeAndAccount(any(), any(), any())).thenReturn(List.of(doomed));
        when(transactions.findByRefundGroupIds(List.of(group))).thenReturn(List.of(doomed, survivor));

        service.deleteRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 1L);

        verify(transactions).save(argThat(leg -> leg.getId().equals(2L)
                && leg.getRefundGroupId() == null
                && leg.getNature() == TransactionNature.INCOME
                && leg.getCategoryId() == null));
    }
}
