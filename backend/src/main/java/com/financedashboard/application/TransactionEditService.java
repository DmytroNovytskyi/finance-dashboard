package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.AccountRepository;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases that modify transactions and statements: categorization, pairing, and deletion. */
@Service
@RequiredArgsConstructor
public class TransactionEditService {

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final BankStatementRepository statements;
    private final AccountRepository accounts;

    /**
     * Applies a category and/or nature to one transaction. When {@code categorySpecified} the
     * transaction's category is set to {@code categoryId} (a null value clears it).
     */
    @Transactional
    public Transaction categorize(Long id, boolean categorySpecified, Long categoryId, TransactionNature nature) {
        if (nature == TransactionNature.TRANSFER) {
            throw new IllegalArgumentException("Set TRANSFER through the transfers endpoint");
        }
        Transaction current = get(id);
        if (categorySpecified && categoryId != null) {
            requireCategory(categoryId);
        }
        Transaction updated = current.toBuilder()
                .categoryId(categorySpecified ? categoryId : current.getCategoryId())
                .nature(nature != null ? nature : current.getNature())
                .build();
        return transactions.save(updated);
    }

    /** Assigns {@code categoryId} (null = uncategorized) to all the given transactions. */
    @Transactional
    public void categorizeBulk(List<Long> ids, Long categoryId) {
        if (categoryId != null) {
            requireCategory(categoryId);
        }
        for (Long id : ids) {
            Transaction current = get(id);
            transactions.save(current.toBuilder().categoryId(categoryId).build());
        }
    }

    /**
     * Clears the category of every categorized, non-transfer transaction (internal transfers keep
     * their reserved tag). Returns how many rows were changed.
     */
    @Transactional
    public int uncategorizeAll() {
        List<Transaction> cleared = transactions.findCategorized().stream()
                .map(transaction -> transaction.toBuilder().categoryId(null).build())
                .toList();
        if (!cleared.isEmpty()) {
            transactions.saveAll(cleared);
        }
        return cleared.size();
    }

    /**
     * Clears the category of the transactions tagged with the given category, without deleting the
     * category. Internal transfers (which keep their reserved tag) are untouched.
     */
    @Transactional
    public int uncategorizeByCategory(Long categoryId) {
        List<Transaction> cleared = transactions.findCategorized().stream()
                .filter(transaction -> categoryId.equals(transaction.getCategoryId()))
                .map(transaction -> transaction.toBuilder().categoryId(null).build())
                .toList();
        if (!cleared.isEmpty()) {
            transactions.saveAll(cleared);
        }
        return cleared.size();
    }

    /**
     * Deletes the account with the given id and everything it holds: all of its transactions (any
     * surviving transfer leg in another account is un-paired back to its natural nature) and its
     * statements, then the account row itself. Returns how many transactions were removed.
     */
    @Transactional
    public int deleteAccountAndTransactions(Long accountId) {
        if (accounts.findById(accountId).isEmpty()) {
            throw new NotFoundException("Account " + accountId + " not found");
        }
        List<Transaction> rows = transactions.findByAccountId(accountId);
        unpairSurvivingTransferLegs(rows);
        if (!rows.isEmpty()) {
            transactions.deleteAll(rows);
        }
        for (BankStatement statement : statements.findByAccountId(accountId)) {
            statements.deleteById(statement.getId());
        }
        accounts.deleteById(accountId);
        return rows.size();
    }

    /** Pairs two own-account transactions as one internal transfer (both legs become TRANSFER). */
    @Transactional
    public List<Transaction> pairTransfer(Long firstId, Long secondId) {
        Transaction first = get(firstId);
        Transaction second = get(secondId);
        if (Objects.equals(first.getAccountId(), second.getAccountId())) {
            throw new IllegalArgumentException("Transfer legs must belong to different accounts");
        }
        if (first.getNature() == TransactionNature.TRANSFER
                || second.getNature() == TransactionNature.TRANSFER) {
            throw new IllegalArgumentException("One of the transactions is already part of a transfer");
        }
        UUID group = UUID.randomUUID();
        Long transferCategory = transferCategoryId();
        Transaction firstLeg = transactions.save(first.toBuilder()
                .nature(TransactionNature.TRANSFER).transferGroupId(group)
                .categoryId(transferCategory).build());
        Transaction secondLeg = transactions.save(second.toBuilder()
                .nature(TransactionNature.TRANSFER).transferGroupId(group)
                .categoryId(transferCategory).build());
        return List.of(firstLeg, secondLeg);
    }

    /**
     * Pairs two transactions as one internal transfer only when neither leg has a category yet.
     * Returns whether the pair was applied; a categorized leg is treated as already decided and is
     * left untouched. Used by import-time auto-pairing.
     */
    @Transactional
    public boolean pairIfBothUncategorized(Long firstId, Long secondId) {
        if (get(firstId).getCategoryId() != null || get(secondId).getCategoryId() != null) {
            return false;
        }
        pairTransfer(firstId, secondId);
        return true;
    }

    /**
     * Reverts an internal transfer: every leg of its group becomes a normal income/expense again
     * (nature by signed amount, category cleared, group removed). Returns the reverted legs.
     */
    @Transactional
    public List<Transaction> unpairTransfer(Long transactionId) {
        Transaction leg = get(transactionId);
        UUID group = leg.getTransferGroupId();
        if (group == null) {
            throw new IllegalArgumentException("Transaction " + transactionId
                    + " is not part of an internal transfer");
        }
        List<Transaction> legs = transactions.findByTransferGroupIds(List.of(group));
        List<Transaction> reverted = legs.stream()
                .map(t -> t.toBuilder()
                        .transferGroupId(null)
                        .nature(TransactionNature.forSignedAmount(t.getAmount()))
                        .categoryId(null)
                        .build())
                .toList();
        return transactions.saveAll(reverted);
    }

    /**
     * Deletes transactions in the inclusive date range (optionally one account). Statements left
     * empty are removed so their files can be re-imported, and an account that ends up with no
     * transactions or statements is removed as well. If a transfer leg is deleted, the surviving
     * leg is un-paired back to its natural nature.
     */
    @Transactional
    public int deleteRange(LocalDate from, LocalDate to, Long accountId) {
        List<Transaction> doomed = transactions.findByDateRangeAndAccount(from, to, accountId);
        if (doomed.isEmpty()) {
            return 0;
        }
        unpairSurvivingTransferLegs(doomed);
        transactions.deleteAll(doomed);
        removeEmptyStatements(doomed);
        removeEmptyAccounts(doomed.stream().map(Transaction::getAccountId).toList());
        return doomed.size();
    }

    /**
     * Deletes the statement with the given id and the transaction rows it introduced, so its file
     * can be re-imported. If one leg of a paired transfer is among those rows, the surviving leg is
     * un-paired back to its natural nature; an account left with no transactions or statements is
     * removed.
     */
    @Transactional
    public int deleteStatement(Long id) {
        BankStatement statement = statements.findById(id)
                .orElseThrow(() -> new NotFoundException("Statement " + id + " not found"));
        List<Transaction> rows = transactions.findByStatementId(id);
        unpairSurvivingTransferLegs(rows);
        if (!rows.isEmpty()) {
            transactions.deleteAll(rows);
        }
        statements.deleteById(id);
        removeEmptyAccounts(List.of(statement.getAccountId()));
        return rows.size();
    }

    private void unpairSurvivingTransferLegs(Collection<Transaction> doomed) {
        Set<Long> doomedIds = doomed.stream().map(Transaction::getId).collect(Collectors.toSet());
        List<UUID> groups = doomed.stream()
                .map(Transaction::getTransferGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (groups.isEmpty()) {
            return;
        }
        List<Transaction> survivingLegs = transactions.findByTransferGroupIds(groups).stream()
                .filter(transaction -> !doomedIds.contains(transaction.getId()))
                .toList();
        for (Transaction leg : survivingLegs) {
            transactions.save(leg.toBuilder()
                    .transferGroupId(null)
                    .nature(TransactionNature.forSignedAmount(leg.getAmount()))
                    .categoryId(null)
                    .build());
        }
    }

    private Long transferCategoryId() {
        return categories.findSystemCategory().map(Category::getId).orElse(null);
    }

    private void removeEmptyStatements(Collection<Transaction> doomed) {
        doomed.stream()
                .map(Transaction::getStatementId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(statementId -> {
                    if (!transactions.existsByStatementId(statementId)) {
                        statements.deleteById(statementId);
                    }
                });
    }

    private void removeEmptyAccounts(Collection<Long> accountIds) {
        accountIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(accountId -> {
                    if (!transactions.existsByAccountId(accountId)
                            && !statements.existsByAccountId(accountId)) {
                        accounts.deleteById(accountId);
                    }
                });
    }

    private Transaction get(Long id) {
        return transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));
    }

    private void requireCategory(Long categoryId) {
        if (!categories.existsById(categoryId)) {
            throw new NotFoundException("Category " + categoryId + " not found");
        }
    }
}
