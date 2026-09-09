package com.financedashboard.domain.port;

import com.financedashboard.domain.statement.BankStatement;
import java.util.List;
import java.util.Optional;

/** Outbound port for storing and reading {@link BankStatement} aggregates. */
public interface BankStatementRepository {

    /** Saves the statement and returns the stored state (with its generated id). */
    BankStatement save(BankStatement statement);

    /** Returns all statements, most recently imported first. */
    List<BankStatement> findAllByOrderByImportedAtDesc();

    /** Returns all statements attributed to the account with the given id. */
    List<BankStatement> findByAccountId(Long accountId);

    /** Returns the statement with the given id, if present. */
    Optional<BankStatement> findById(Long id);

    /** Returns whether a statement with the given file hash was already imported. */
    boolean existsByFileHash(String fileHash);

    /** Returns whether any statement belongs to the account with the given id. */
    boolean existsByAccountId(Long accountId);

    /** Deletes the statement with the given id. */
    void deleteById(Long id);
}
