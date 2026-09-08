package com.financedashboard.domain.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** An account the user owns. Accounts are created and renamed but never deleted. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Account {

    private final Long id;
    private final String name;
    private final String currency;
    private final AccountKind kind;
    private final String accountNumber;
    private final int sortOrder;
}
