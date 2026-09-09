package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request body for creating a merchant-to-category default. */
public record MerchantRuleCreateRequest(
        @NotBlank(message = "merchant must not be blank")
        String merchant,

        @NotNull(message = "categoryId is required")
        @Positive(message = "categoryId must be positive")
        Long categoryId) {
}
