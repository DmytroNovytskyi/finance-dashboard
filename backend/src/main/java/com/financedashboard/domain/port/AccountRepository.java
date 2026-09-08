package com.financedashboard.domain.port;

import com.financedashboard.domain.account.Account;
import com.financedashboard.domain.account.AccountKind;
import java.util.List;
import java.util.Optional;

/** Outbound port for storing and reading {@link Account} aggregates. */
public interface AccountRepository {

    /** Saves the account and returns the stored state (with its generated id). */
    Account save(Account account);

    /** Returns the account with the given id, if present. */
    Optional<Account> findById(Long id);

    /** Returns all accounts ordered by sort order then name. */
    List<Account> findAll();

    /** Returns accounts tagged with the given kind, ordered by sort order then name. */
    List<Account> findByKind(AccountKind kind);

    /** Returns whether an account with the given id exists. */
    boolean existsById(Long id);
}
