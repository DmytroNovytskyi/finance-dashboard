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

    /**
     * Returns the first account carrying the given canonical (digits-only) account number, if any.
     * Used to resolve the account a statement belongs to on import.
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Returns the first account in the given currency, if any. Fallback for statements that do not
     * name their account number.
     */
    Optional<Account> findFirstByCurrency(String currency);

    /** Deletes the account with the given id. */
    void deleteById(Long id);
}
