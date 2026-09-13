package com.financedashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.application.MerchantRuleService.RuleDetail;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.merchant_rule.MatchType;
import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.MerchantRuleRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantRuleServiceTest {

    private static final long CATEGORY = 7L;

    @Mock
    private MerchantRuleRepository rules;

    @Mock
    private CategoryRepository categories;

    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private MerchantRuleService service;

    private static Category category(long id, String name) {
        return Category.builder().id(id).name(name).color("#123456").sortOrder(0).build();
    }

    @Test
    void createNormalizesMerchantAndResolvesCategory() {
        when(rules.existsByMerchant("EXAMPLE MERCHANT")).thenReturn(false);
        when(categories.findById(CATEGORY)).thenReturn(Optional.of(category(CATEGORY, "Taxes")));
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RuleDetail created = service.create("  Example Merchant  ", CATEGORY, MatchType.EQUALS);

        assertThat(created.merchant()).isEqualTo("EXAMPLE MERCHANT");
        assertThat(created.categoryName()).isEqualTo("Taxes");
        assertThat(created.categoryId()).isEqualTo(CATEGORY);
    }

    @Test
    void createRejectsBlankMerchant() {
        assertThatThrownBy(() -> service.create("   ", CATEGORY, MatchType.EQUALS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsUnknownCategory() {
        when(categories.findById(CATEGORY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create("Merchant", CATEGORY, MatchType.EQUALS))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRejectsAReservedCategory() {
        when(categories.findById(CATEGORY)).thenReturn(Optional.of(Category.builder()
                .id(CATEGORY).name("Refund").system(true).systemKey("REFUND").build()));

        assertThatThrownBy(() -> service.create("EXAMPLE SHOP", CATEGORY, MatchType.EQUALS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(rules, never()).save(any());
    }

    @Test
    void createRejectsDuplicateMerchant() {
        when(categories.findById(CATEGORY)).thenReturn(Optional.of(category(CATEGORY, "Taxes")));
        when(rules.existsByMerchant("EXAMPLE MERCHANT")).thenReturn(true);
        assertThatThrownBy(() -> service.create("example merchant", CATEGORY, MatchType.EQUALS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listJoinsCategoryDisplayFields() {
        MerchantRule rule = MerchantRule.builder().id(3L).merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findAll()).thenReturn(List.of(rule));
        when(categories.findAll()).thenReturn(List.of(category(CATEGORY, "Taxes")));

        RuleDetail detail = service.list().get(0);

        assertThat(detail.merchant()).isEqualTo("EXAMPLE MERCHANT");
        assertThat(detail.categoryName()).isEqualTo("Taxes");
        assertThat(detail.color()).isEqualTo("#123456");
    }

    @Test
    void deleteUnknownRuleThrowsNotFound() {
        when(rules.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteAlsoRemovesTheRuleItself() {
        MerchantRule rule = MerchantRule.builder().id(4L).merchant("EXAMPLE PAYER").categoryId(CATEGORY).build();
        when(rules.findById(4L)).thenReturn(Optional.of(rule));
        when(transactions.findCategorized()).thenReturn(List.of());

        assertThat(service.delete(4L)).isZero();
        verify(rules).deleteById(4L);
    }

    @Test
    void deleteExistingRuleRemovesItAndRevertsItsTransactions() {
        MerchantRule rule = MerchantRule.builder().id(3L).merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findById(3L)).thenReturn(Optional.of(rule));
        Transaction used = transaction(1L, "Example Merchant").toBuilder().categoryId(CATEGORY).build();
        Transaction untouched = transaction(2L, "EXAMPLE STORE").toBuilder().categoryId(CATEGORY).build();
        when(transactions.findCategorized()).thenReturn(List.of(used, untouched));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int reverted = service.delete(3L);

        verify(rules).deleteById(3L);
        assertThat(reverted).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getId()).isEqualTo(1L);
        assertThat(captor.getValue().get(0).getCategoryId()).isNull();
    }

    @Test
    void unlinkRevertsTaggedTransactionsAndKeepsTheRule() {
        MerchantRule rule = MerchantRule.builder().id(3L).merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findById(3L)).thenReturn(Optional.of(rule));
        Transaction used = transaction(1L, "Example Merchant").toBuilder().categoryId(CATEGORY).build();
        Transaction otherCategory = transaction(2L, "Example Merchant").toBuilder().categoryId(9L).build();
        Transaction otherMerchant = transaction(3L, "EXAMPLE STORE").toBuilder().categoryId(CATEGORY).build();
        when(transactions.findCategorized()).thenReturn(List.of(used, otherCategory, otherMerchant));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int reverted = service.unlink(3L);

        verify(rules, never()).deleteById(3L);
        assertThat(reverted).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getId()).isEqualTo(1L);
        assertThat(captor.getValue().get(0).getCategoryId()).isNull();
    }

    @Test
    void unlinkWithNothingTaggedIsANoOp() {
        MerchantRule rule = MerchantRule.builder().id(3L).merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findById(3L)).thenReturn(Optional.of(rule));
        when(transactions.findCategorized()).thenReturn(List.of());

        assertThat(service.unlink(3L)).isZero();
        verify(transactions, never()).saveAll(any());
        verify(rules, never()).deleteById(3L);
    }

    @Test
    void unlinkUnknownRuleThrowsNotFound() {
        when(rules.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.unlink(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void applyAssignsOnlyExactNormalizedMerchants() {
        MerchantRule rule = MerchantRule.builder().merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findAll()).thenReturn(List.of(rule));
        Transaction match = transaction(1L, "Example Merchant");
        Transaction different = transaction(2L, "EXAMPLE STORE");
        Transaction noMerchant = transaction(3L, null);
        when(transactions.findUncategorized()).thenReturn(List.of(match, different, noMerchant));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int applied = service.applyToUncategorized();

        assertThat(applied).isEqualTo(1);
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getCategoryId()).isEqualTo(CATEGORY);
    }

    @Test
    void applyWithNoRulesReturnsZeroWithoutScanning() {
        when(rules.findAll()).thenReturn(List.of());
        assertThat(service.applyToUncategorized()).isZero();
        verify(transactions, never()).findUncategorized();
    }

    @Test
    void applyOneRuleTagsOnlyItsMatchingRows() {
        MerchantRule rule = MerchantRule.builder().id(5L).merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findById(5L)).thenReturn(Optional.of(rule));
        when(transactions.findUncategorized()).thenReturn(List.of(
                transaction(1L, "Example Merchant"),
                transaction(2L, "EXAMPLE STORE")));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int applied = service.apply(5L);

        assertThat(applied).isEqualTo(1);
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getCategoryId()).isEqualTo(CATEGORY);
    }

    @Test
    void applyUnknownRuleThrowsNotFound() {
        when(rules.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply(9L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createStoresTheMatchTypeItWasGiven() {
        when(categories.findById(CATEGORY)).thenReturn(Optional.of(category(CATEGORY, "Taxes")));
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RuleDetail created = service.create("EXAMPLE SHOP", CATEGORY, MatchType.CONTAINS);

        assertThat(created.matchType()).isEqualTo(MatchType.CONTAINS);
        ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
        verify(rules).save(captor.capture());
        assertThat(captor.getValue().getMatchType()).isEqualTo(MatchType.CONTAINS);
    }

    @Test
    void createWithoutAMatchTypeFallsBackToExactMatching() {
        when(categories.findById(CATEGORY)).thenReturn(Optional.of(category(CATEGORY, "Taxes")));
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create("EXAMPLE SHOP", CATEGORY, null).matchType()).isEqualTo(MatchType.EQUALS);
    }

    @Test
    void containsRuleTagsCounterpartiesThatWrapItsText() {
        MerchantRule rule = MerchantRule.builder().merchant("EXAMPLE SHOP")
                .matchType(MatchType.CONTAINS).categoryId(CATEGORY).build();
        when(rules.findAll()).thenReturn(List.of(rule));
        when(transactions.findUncategorized()).thenReturn(List.of(
                transaction(1L, "EXAMPLE SHOP SP. Z O.O."),
                transaction(2L, "CARD PAYMENT EXAMPLE SHOP POZNAN"),
                transaction(3L, "EXAMPLE MARKET")));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.applyToUncategorized()).isEqualTo(2);
    }

    @Test
    void theNarrowerRuleDecidesWhenTwoWouldTagTheSameRow() {
        MerchantRule broad = MerchantRule.builder().id(1L).merchant("EXAMPLE SHOP")
                .matchType(MatchType.CONTAINS).categoryId(CATEGORY).build();
        MerchantRule narrow = MerchantRule.builder().id(2L).merchant("EXAMPLE SHOP SP. Z O.O.")
                .matchType(MatchType.EQUALS).categoryId(9L).build();
        when(rules.findAll()).thenReturn(List.of(broad, narrow));
        when(transactions.findUncategorized()).thenReturn(List.of(transaction(1L, "EXAMPLE SHOP SP. Z O.O.")));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.applyToUncategorized();

        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getCategoryId()).isEqualTo(9L);
    }

    @Test
    void unlinkRevertsTheRowsAContainsRuleTagged() {
        MerchantRule rule = MerchantRule.builder().id(5L).merchant("EXAMPLE SHOP")
                .matchType(MatchType.CONTAINS).categoryId(CATEGORY).build();
        when(rules.findById(5L)).thenReturn(Optional.of(rule));
        Transaction wrapped = transaction(1L, "CARD PAYMENT EXAMPLE SHOP").toBuilder().categoryId(CATEGORY).build();
        Transaction otherMerchant = transaction(2L, "EXAMPLE STORE").toBuilder().categoryId(CATEGORY).build();
        when(transactions.findCategorized()).thenReturn(List.of(wrapped, otherMerchant));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.unlink(5L)).isEqualTo(1);
    }

    @Test
    void normalizeCollapsesCaseAndWhitespace() {
        assertThat(MerchantRuleService.normalize("  Example\tmerchant ")).isEqualTo("EXAMPLE MERCHANT");
        assertThat(MerchantRuleService.normalize(null)).isEmpty();
    }

    @Test
    void clearAllDeletesRulesAndRevertsTransactionsThatUsedThem() {
        MerchantRule rule = MerchantRule.builder().id(5L).merchant("EXAMPLE MERCHANT").categoryId(CATEGORY).build();
        when(rules.findAll()).thenReturn(List.of(rule));
        Transaction reverted = transaction(1L, "Example Merchant").toBuilder().categoryId(CATEGORY).build();
        Transaction otherCategory = transaction(2L, "Example Merchant").toBuilder().categoryId(9L).build();
        Transaction otherMerchant = transaction(3L, "EXAMPLE STORE").toBuilder().categoryId(CATEGORY).build();
        when(transactions.findCategorized()).thenReturn(List.of(reverted, otherCategory, otherMerchant));
        when(transactions.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MerchantRuleService.ClearAllResult result = service.clearAll();

        verify(rules).deleteById(5L);
        assertThat(result.rulesRemoved()).isEqualTo(1);
        assertThat(result.transactionsUncategorized()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactions).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getCategoryId()).isNull();
    }

    @Test
    void clearAllWithNoRulesIsANoOp() {
        when(rules.findAll()).thenReturn(List.of());

        MerchantRuleService.ClearAllResult result = service.clearAll();

        assertThat(result.rulesRemoved()).isZero();
        assertThat(result.transactionsUncategorized()).isZero();
        verify(transactions, never()).findCategorized();
    }

    private static Transaction transaction(long id, String merchant) {
        return Transaction.builder()
                .id(id)
                .statementId(1L)
                .accountId(1L)
                .transactionDate(LocalDate.of(2026, 3, 1))
                .amount(BigDecimal.valueOf(-100))
                .currency("PLN")
                .merchant(merchant)
                .build();
    }
}
