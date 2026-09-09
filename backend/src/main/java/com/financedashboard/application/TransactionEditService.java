package com.financedashboard.application;

import com.financedashboard.application.exception.NotFoundException;
import com.financedashboard.domain.category.Category;
import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.port.CategoryRepository;
import com.financedashboard.domain.port.TransactionRepository;
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
     * Deletes transactions in the inclusive date range (optionally one account). Statements left
     * empty are removed so their files can be re-imported. If a transfer leg is deleted, the
     * surviving leg is un-paired back to its natural nature.
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
        return doomed.size();
    }

    /**
     * Deletes the statement with the given id and the transaction rows it introduced, so its file
     * can be re-imported. If one leg of a paired transfer is among those rows, the surviving leg is
     * un-paired back to its natural nature.
     */
    @Transactional
    public int deleteStatement(Long id) {
        statements.findById(id)
                .orElseThrow(() -> new NotFoundException("Statement " + id + " not found"));
        List<Transaction> rows = transactions.findByStatementId(id);
        unpairSurvivingTransferLegs(rows);
        if (!rows.isEmpty()) {
            transactions.deleteAll(rows);
        }
        statements.deleteById(id);
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
