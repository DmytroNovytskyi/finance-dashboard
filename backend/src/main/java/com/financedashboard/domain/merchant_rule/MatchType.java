package com.financedashboard.domain.merchant_rule;

/**
 * How a rule's text is compared against a transaction's counterparty.
 *
 * <p>Both sides are compared in normalized form — trimmed, uppercased, whitespace collapsed — so
 * case and spacing never matter and a rule is written the way the counterparty reads.
 *
 * <p>Each constant carries a {@link #specificity()}: the lower the value, the narrower the match,
 * and a narrower rule always wins over a wider one that also matches. See
 * {@link MerchantRuleMatcher} for how a counterparty is resolved when several rules claim it.
 */
public enum MatchType {

    /** The counterparty is exactly the rule's text. The behaviour every rule had before it existed. */
    EQUALS(0),

    /**
     * The counterparty begins with the rule's text; {@code EXAMPLE SHOP} claims
     * {@code EXAMPLE SHOP SP. Z O.O.}.
     */
    STARTS_WITH(1),

    /** The counterparty contains the rule's text anywhere; {@code EXAMPLE SHOP} claims a card-payment line too. */
    CONTAINS(2);

    private final int specificity;

    MatchType(int specificity) {
        this.specificity = specificity;
    }

    /** The rank that decides which of two matching rules wins: lower is narrower and takes precedence. */
    public int specificity() {
        return specificity;
    }

    /**
     * Whether a normalized counterparty satisfies this rule, given the rule's normalized text.
     *
     * @param merchant the normalized counterparty of the transaction
     * @param text     the normalized text the rule was saved with
     */
    public boolean matches(String merchant, String text) {
        return switch (this) {
            case EQUALS -> merchant.equals(text);
            case STARTS_WITH -> merchant.startsWith(text);
            case CONTAINS -> merchant.contains(text);
        };
    }
}
