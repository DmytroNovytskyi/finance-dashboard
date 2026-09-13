package com.financedashboard.domain.merchant_rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A user-managed default: transactions whose counterparty ({@code merchant}) matches this rule's
 * merchant are tagged with {@code categoryId} when imported, and can be tagged on demand from the
 * already-imported history. Both sides are compared normalized — case and spacing never matter.
 *
 * <p>{@code matchType} says how the two are compared, and the stored text is the normalized form of
 * whatever the user typed. Resolving several rules that claim one counterparty is
 * {@link MerchantRuleMatcher}'s job, not this record's.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class MerchantRule {

    private final Long id;
    private final String merchant;
    private final Long categoryId;

    /**
     * How {@code merchant} is compared. Never null: the column is {@code NOT NULL DEFAULT 'EQUALS'},
     * so a rule saved before match types existed reads back as {@code EQUALS} and behaves exactly as
     * it did then.
     */
    @Builder.Default
    private final MatchType matchType = MatchType.EQUALS;
}
