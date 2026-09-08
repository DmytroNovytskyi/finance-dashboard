package com.financedashboard.web.dto;

import com.financedashboard.domain.account.AccountKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request body for creating an account. */
public record AccountCreateRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        @NotBlank(message = "currency must not be blank")
        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        AccountKind kind,

        String accountNumber,

        Integer sortOrder) {
}
