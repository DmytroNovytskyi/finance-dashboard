package com.financedashboard.domain.account;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * An account the user owns. An account is normally created automatically from an imported
 * statement and disappears once it holds no transactions; {@code kind} and {@code name} are
 * user-managed, {@code currency} and {@code accountNumber} follow the statements of the account.
 */
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
