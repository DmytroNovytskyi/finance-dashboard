package com.financedashboard.web.dto;

import com.financedashboard.domain.merchant_rule.MatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for creating a merchant-to-category default.
 *
 * <p>{@code matchType} is optional and defaults to {@code EQUALS}, so a client that does not send it
 * — including any that predates match types — gets exact matching, the only behaviour that existed
 * before them.
 */
public record MerchantRuleCreateRequest(
        @NotBlank(message = "merchant must not be blank")
        String merchant,

        @NotNull(message = "categoryId is required")
        @Positive(message = "categoryId must be positive")
        Long categoryId,

        MatchType matchType) {
}
