package com.financedashboard.web.dto;

import com.financedashboard.domain.account.AccountKind;
import jakarta.validation.constraints.Pattern;

/** Partial update for an account; null fields leave the value unchanged. */
public record AccountUpdateRequest(
        String name,

        @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        AccountKind kind,

        String accountNumber) {
}
