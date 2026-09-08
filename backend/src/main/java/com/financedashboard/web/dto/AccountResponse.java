package com.financedashboard.web.dto;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;

/** API representation of an {@link Account}. */
public record AccountResponse(
        Long id,
        String name,
        String currency,
        AccountKind kind,
        String accountNumber,
        int sortOrder) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getCurrency(),
                account.getKind(),
                account.getAccountNumber(),
                account.getSortOrder());
    }
}
