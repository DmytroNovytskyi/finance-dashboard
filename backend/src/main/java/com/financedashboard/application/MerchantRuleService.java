package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.merchant_rule.MatchType;
import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.merchant_rule.MerchantRuleMatcher;
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

    /** A rule joined with its category's display fields and the rows it currently claims. */
    public record RuleDetail(
            Long id,
            String merchant,
            MatchType matchType,
            Long categoryId,
            String categoryName,
            String color,
            int claimedRows) {
    }

    /** Returns all rules with their resolved category fields, in insertion order. */
    public List<RuleDetail> list() {
        Map<Long, Category> categoryById = categories.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Transaction> tagged = transactions.findCategorized();
        return rules.findAll().stream()
                .map(rule -> detail(rule, categoryById.get(rule.getCategoryId()), claims(rule, tagged)))
                .toList();
    }

    /**
     * Creates a rule for the normalized merchant and category; duplicates are rejected. A reserved
     * category is refused: a default tagging rows with one would dress every match up as a linked
     * leg that no pairing flow ever created, and it would do so in bulk on the next apply.
     *
     * <p>A null {@code matchType} is exact matching, which is what a client that predates match
     * types sends and what every rule stored before them means.
     */
    @Transactional
    public RuleDetail create(String merchant, Long categoryId, MatchType matchType) {
        String key = normalize(merchant);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("merchant must not be blank");
        }
        Category category = requireAssignableCategory(categoryId);
        if (rules.existsByMerchant(key)) {
            throw new IllegalArgumentException("A default for merchant '" + key + "' already exists");
        }
        MerchantRule saved = rules.save(MerchantRule.builder()
                .merchant(key)
                .matchType(matchType == null ? MatchType.EQUALS : matchType)
                .categoryId(categoryId)
                .build());
        return detail(saved, category, claims(saved, transactions.findCategorized()));
    }

    /**
     * Un-links the rule with the given id while keeping it: the categorized, non-transfer
     * transactions it tagged (same merchant and the rule's category) become uncategorized again,
     * but the rule stays and keeps tagging future imports. Returns how many rows were reverted.
     */
    @Transactional
    public int unlink(Long id) {
        MerchantRule rule = rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant rule " + id + " not found"));
        return revertTagged(List.of(rule));
    }

    /**
     * Deletes the rule with the given id and un-links it: the categorized, non-transfer
     * transactions that were tagged through it (same merchant and the rule's category) become
     * uncategorized again. Returns how many rows were reverted.
     */
    @Transactional
    public int delete(Long id) {
        int reverted = unlink(id);
        rules.deleteById(id);
        return reverted;
    }

    /**
     * Uncategorizes every categorized, non-transfer transaction tagged through the given
     * merchant-to-category links. Returns how many rows were reverted.
     */
    private int revertTagged(List<MerchantRule> candidates) {
        List<Transaction> reverted = transactions.findCategorized().stream()
                .filter(transaction -> usesLink(transaction, candidates))
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
        for (MerchantRule rule : all) {
            rules.deleteById(rule.getId());
        }
        return new ClearAllResult(all.size(), revertTagged(all));
    }

    private static boolean usesLink(Transaction transaction, List<MerchantRule> candidates) {
        if (transaction.getMerchant() == null || transaction.getCategoryId() == null) {
            return false;
        }
        Long ruleCategory = MerchantRuleMatcher.categoryFor(normalize(transaction.getMerchant()), candidates);
        return ruleCategory != null && ruleCategory.equals(transaction.getCategoryId());
    }

    /** Number of defaults removed and transactions reverted by {@link #clearAll()}. */
    public record ClearAllResult(int rulesRemoved, int transactionsUncategorized) {
    }

    /**
     * Applies every rule to the current uncategorized, non-transfer transactions whose merchant
     * matches it, by the rule's own {@link MatchType}. Returns how many rows were categorized.
     */
    @Transactional
    public int applyToUncategorized() {
        List<MerchantRule> all = rules.findAll();
        if (all.isEmpty()) {
            return 0;
        }
        return applyTo(all);
    }

    /**
     * Applies the single rule to the uncategorized rows its merchant matches; returns the count.
     * Only this rule is considered, so a broader rule that also claims those rows does not decide
     * the outcome of applying this one.
     */
    @Transactional
    public int apply(Long id) {
        MerchantRule rule = rules.findById(id)
                .orElseThrow(() -> new NotFoundException("Merchant rule " + id + " not found"));
        return applyTo(List.of(rule));
    }

    private int applyTo(List<MerchantRule> candidates) {
        List<Transaction> changed = transactions.findUncategorized().stream()
                .map(transaction -> apply(transaction, candidates))
                .filter(transaction -> transaction != null)
                .toList();
        if (!changed.isEmpty()) {
            transactions.saveAll(changed);
        }
        return changed.size();
    }

    private static Transaction apply(Transaction transaction, List<MerchantRule> candidates) {
        String merchant = transaction.getMerchant();
        if (merchant == null || merchant.isBlank()) {
            return null;
        }
        Long categoryId = MerchantRuleMatcher.categoryFor(normalize(merchant), candidates);
        if (categoryId == null) {
            return null;
        }
        return transaction.toBuilder().categoryId(categoryId).build();
    }

    /** Normalizes a counterparty for comparison: trimmed, uppercased, whitespace collapsed. */
    public static String normalize(String merchant) {
        return merchant == null ? "" : merchant.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    /**
     * Returns the category with the given id, refusing the reserved ones: a default pointing at one
     * would tag every matching transaction the way the pairing flows do, with no pair behind it.
     */
    private Category requireAssignableCategory(Long categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category " + categoryId + " not found"));
        if (category.isSystem()) {
            throw new IllegalArgumentException("Category '" + category.getName()
                    + "' is reserved and cannot be set as a default; link the transactions instead");
        }
        return category;
    }

    /**
     * How many rows this rule on its own would revert were it deleted: the categorized, non-transfer
     * transactions whose merchant it matches and whose category is the one it files them under. That
     * is the test {@link #revertTagged(List)} applies to a single rule, so the damage a client is
     * shown before deleting a default is the damage the deletion reports back.
     */
    private static int claims(MerchantRule rule, List<Transaction> tagged) {
        List<MerchantRule> alone = List.of(rule);
        return (int) tagged.stream().filter(transaction -> usesLink(transaction, alone)).count();
    }

    private static RuleDetail detail(MerchantRule rule, Category category, int claimedRows) {
        return new RuleDetail(rule.getId(), rule.getMerchant(), rule.getMatchType(), rule.getCategoryId(),
                category == null ? null : category.getName(),
                category == null ? null : category.getColor(),
                claimedRows);
    }
}
