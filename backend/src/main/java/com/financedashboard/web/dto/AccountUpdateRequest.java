package com.financedashboard.web.dto;

import com.financedashboard.domain.account.AccountKind;
import jakarta.validation.constraints.NotBlank;

/**
 * The user-owned fields of an account. The currency and the account number are written by the
 * statement import that creates the account and are deliberately absent here, so no client can
 * change them. A null kind clears the personal/business tag.
 */
public record AccountUpdateRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        AccountKind kind) {
}
