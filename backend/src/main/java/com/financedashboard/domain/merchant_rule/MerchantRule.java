package com.financedashboard.domain.merchant_rule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A user-managed default: transactions whose counterparty ({@code merchant}) matches this rule's
 * merchant are tagged with {@code categoryId} when imported. Matching is exact, ignoring case and
 * spacing (the merchant is stored normalized).
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class MerchantRule {

    private final Long id;
    private final String merchant;
    private final Long categoryId;
}
