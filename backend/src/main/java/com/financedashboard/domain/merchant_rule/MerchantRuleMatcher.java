package com.financedashboard.domain.merchant_rule;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves a counterparty against a set of rules.
 *
 * <p>Once matching stopped being exact, more than one rule could claim the same counterparty:
 * {@code EXAMPLE SHOP} as a containment and {@code EXAMPLE SHOP SP. Z O.O.} as an equality both claim
 * that shop. The winner is settled by {@link #BY_SPECIFICITY}, so the outcome does not depend on the
 * order the rules happen to be stored in, and a narrow rule can be added to refine a broad one
 * without deleting it.
 *
 * <p>Every caller that answers "which category does this counterparty fall under" goes through
 * here — the import that auto-tags, the apply actions, and the revert that undoes a deleted rule.
 * They have to agree: a rule that tags rows on import but does not recognise them when deleted
 * would leave those rows tagged with nothing to point at.
 */
public final class MerchantRuleMatcher {

    private MerchantRuleMatcher() {
    }

    /**
     * The narrower match wins; among equally narrow ones the longer text wins, so a rule naming more
     * of the counterparty beats one naming less of it. The id settles the rest, which only happens
     * for two rules of the same type and the same length — rules being unique by text, that leaves
     * the result deterministic rather than meaningful.
     */
    private static final Comparator<MerchantRule> BY_SPECIFICITY =
            Comparator.comparingInt((MerchantRule rule) -> rule.getMatchType().specificity())
                    .thenComparing(
                            (MerchantRule rule) -> rule.getMerchant().length(),
                            Comparator.reverseOrder())
                    .thenComparing(MerchantRule::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Returns the category of the rule that claims this normalized counterparty, or null when no
     * rule does.
     *
     * @param merchant the normalized counterparty, as {@code MerchantRuleService.normalize} writes it
     * @param rules    the rules to consider, in any order
     */
    public static Long categoryFor(String merchant, List<MerchantRule> rules) {
        return rules.stream()
                .filter(rule -> rule.getMatchType().matches(merchant, rule.getMerchant()))
                .min(BY_SPECIFICITY)
                .map(MerchantRule::getCategoryId)
                .orElse(null);
    }
}
