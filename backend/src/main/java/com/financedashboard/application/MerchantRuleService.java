package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.MerchantRuleRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.Transaction;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for merchant-to-category defaults: a saved rule tags future imports that carry the same
 * counterparty, and can be applied to the already-imported uncategorized history on demand.
 */
@Service
@RequiredArgsConstructor
public class MerchantRuleService {

    private final MerchantRuleRepository rules;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    /** A rule joined with its category's display fields, for the API. */
    public record RuleDetail(
            Long id,
            String merchant,
            Long categoryId,
            String categoryName,
            String color) {
    }

    /** Returns all rules with their resolved category fields, in insertion order. */
    public List<RuleDetail> list() {
        Map<Long, Category> categoryById = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        return rules.findAll().stream()
                .map(rule -> detail(rule, categoryById.get(rule.getCategoryId())))
                .toList();
    }

    /** Creates a rule for the normalized merchant and category; duplicates are rejected. */
    @Transactional
    public RuleDetail create(String merchant, Long categoryId) {
        String key = normalize(merchant);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("merchant must not be blank");
        }
        requireCategory(categoryId);
        if (rules.existsByMerchant(key)) {
            throw new IllegalArgumentException("A default for merchant '" + key + "' already exists");
        }
        MerchantRule saved = rules.save(MerchantRule.builder()
                .merchant(key)
                .categoryId(categoryId)
                .build());
        return detail(saved, categories.findById(categoryId).orElseThrow());
    }

    /**
     * Deletes the rule with the given id and un-links it: the categorized, non-transfer
     * transactions that were tagged through it (same merchant and the rule's category) become
     * uncategorized again. Returns how many rows were reverted.
     */
    @Transactional
    public int delete(Long id) {
        MerchantRule rule = rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant rule " + id + " not found"));
        rules.deleteById(id);
        Map<String, Long> categoryByMerchant = Map.of(rule.getMerchant(), rule.getCategoryId());
        List<Transaction> reverted = transactions.findCategorized().stream()
                .filter(transaction -> usesLink(transaction, categoryByMerchant))
                .map(transaction -> transaction.toBuilder().categoryId(null).build())
                .toList();
        if (!reverted.isEmpty()) {
            transactions.saveAll(reverted);
        }
        return reverted.size();
    }

    /**
     * Deletes every default rule and reverts the transactions it had auto-tagged: a categorized,
     * non-transfer row whose merchant matches a removed rule and whose category equals that rule's
     * category becomes uncategorized again. Returns the number of reverted transactions.
     */
    @Transactional
    public ClearAllResult clearAll() {
        List<MerchantRule> all = rules.findAll();
        if (all.isEmpty()) {
            return new ClearAllResult(0, 0);
        }
        Map<String, Long> categoryByMerchant = all.stream()
                .collect(Collectors.toMap(MerchantRule::getMerchant, MerchantRule::getCategoryId));
        for (MerchantRule rule : all) {
            rules.deleteById(rule.getId());
        }
        List<Transaction> reverted = transactions.findCategorized().stream()
                .filter(transaction -> usesLink(transaction, categoryByMerchant))
                .map(transaction -> transaction.toBuilder().categoryId(null).build())
                .toList();
        if (!reverted.isEmpty()) {
            transactions.saveAll(reverted);
        }
        return new ClearAllResult(all.size(), reverted.size());
    }

    private static boolean usesLink(Transaction transaction, Map<String, Long> categoryByMerchant) {
        if (transaction.getMerchant() == null || transaction.getCategoryId() == null) {
            return false;
        }
        Long ruleCategory = categoryByMerchant.get(normalize(transaction.getMerchant()));
        return ruleCategory != null && ruleCategory.equals(transaction.getCategoryId());
    }

    /** Number of defaults removed and transactions reverted by {@link #clearAll()}. */
    public record ClearAllResult(int rulesRemoved, int transactionsUncategorized) {
    }

    /**
     * Applies every rule to the current uncategorized, non-transfer transactions whose merchant
     * matches exactly (case- and spacing-insensitively). Returns how many rows were categorized.
     */
    @Transactional
    public int applyToUncategorized() {
        Map<String, Long> categoryByMerchant = rules.findAll().stream()
                .collect(Collectors.toMap(MerchantRule::getMerchant, MerchantRule::getCategoryId));
        if (categoryByMerchant.isEmpty()) {
            return 0;
        }
        List<Transaction> changed = transactions.findUncategorized().stream()
                .map(transaction -> apply(transaction, categoryByMerchant))
                .filter(transaction -> transaction != null)
                .toList();
        if (!changed.isEmpty()) {
            transactions.saveAll(changed);
        }
        return changed.size();
    }

    /** Applies the single rule to the uncategorized rows that match its merchant; returns the count. */
    @Transactional
    public int apply(Long id) {
        MerchantRule rule = rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant rule " + id + " not found"));
        Map<String, Long> categoryByMerchant = Map.of(rule.getMerchant(), rule.getCategoryId());
        List<Transaction> changed = transactions.findUncategorized().stream()
                .map(transaction -> apply(transaction, categoryByMerchant))
                .filter(transaction -> transaction != null)
                .toList();
        if (!changed.isEmpty()) {
            transactions.saveAll(changed);
        }
        return changed.size();
    }

    private static Transaction apply(Transaction transaction, Map<String, Long> categoryByMerchant) {
        String merchant = transaction.getMerchant();
        if (merchant == null || merchant.isBlank()) {
            return null;
        }
        Long categoryId = categoryByMerchant.get(normalize(merchant));
        if (categoryId == null) {
            return null;
        }
        return transaction.toBuilder().categoryId(categoryId).build();
    }

    /** Normalizes a counterparty for comparison: trimmed, uppercased, whitespace collapsed. */
    public static String normalize(String merchant) {
        return merchant == null ? "" : merchant.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    private void requireCategory(Long categoryId) {
        if (!categories.existsById(categoryId)) {
            throw new NotFoundException("Category " + categoryId + " not found");
        }
    }

    private static RuleDetail detail(MerchantRule rule, Category category) {
        return new RuleDetail(rule.getId(), rule.getMerchant(), rule.getCategoryId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getColor());
    }
}
