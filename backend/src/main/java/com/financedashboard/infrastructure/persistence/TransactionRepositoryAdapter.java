package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import com.financedashboard.infrastructure.persistence.mapper.TransactionMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link TransactionRepository}. */
@Component
@RequiredArgsConstructor
public class TransactionRepositoryAdapter implements TransactionRepository {

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("transactionDate"),
            Sort.Order.desc("id"));

    private final TransactionJpaRepository jpa;
    private final TransactionMapper mapper;

    @Override
    public Transaction save(Transaction transaction) {
        return mapper.toDomain(jpa.save(mapper.toEntity(transaction)));
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Transaction> saveAll(Collection<Transaction> transactions) {
        List<TransactionEntity> entities = transactions.stream().map(mapper::toEntity).toList();
        return mapper.toDomain(jpa.saveAll(entities));
    }

    @Override
    public Set<String> findExistingDedupHashes(Long accountId, Collection<String> hashes) {
        return jpa.findByAccountIdAndDedupHashIn(accountId, hashes).stream()
                .map(TransactionEntity::getDedupHash)
                .collect(Collectors.toSet());
    }

    @Override
    public List<Transaction> findByDateRangeAndAccount(LocalDate from, LocalDate to, Long accountId) {
        Specification<TransactionEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.between(root.get("transactionDate"), from, to));
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        List<TransactionEntity> entities = jpa.findAll(spec,
                Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("id")));
        return mapper.toDomain(entities);
    }

    @Override
    public List<Transaction> findNonTransfers(LocalDate from, LocalDate to, Collection<Long> accountIds) {
        Specification<TransactionEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("nature"), TransactionNature.TRANSFER));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
            }
            if (accountIds != null) {
                predicates.add(root.get("accountId").in(accountIds));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        List<TransactionEntity> entities = jpa.findAll(spec,
                Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("id")));
        return mapper.toDomain(entities);
    }

    @Override
    public List<Transaction> findByTransferGroupIds(Collection<UUID> transferGroupIds) {
        return mapper.toDomain(jpa.findByTransferGroupIdIn(transferGroupIds));
    }

    @Override
    public boolean existsByStatementId(Long statementId) {
        return jpa.existsByStatementId(statementId);
    }

    @Override
    public List<Transaction> findByStatementId(Long statementId) {
        return mapper.toDomain(jpa.findByStatementId(statementId));
    }

    @Override
    public Map<Long, Long> countByStatementIds(Collection<Long> statementIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (TransactionJpaRepository.StatementTransactionCount row
                : jpa.countByStatementIdIn(statementIds)) {
            counts.put(row.getStatementId(), row.getTransactionCount());
        }
        return counts;
    }

    @Override
    public void deleteAll(Collection<Transaction> transactions) {
        List<Long> ids = transactions.stream().map(Transaction::getId).toList();
        jpa.deleteAllByIdInBatch(ids);
    }

    @Override
    public List<Transaction> findAllNonTransfers() {
        return mapper.toDomain(jpa.findByNatureNotOrderByTransactionDateAscIdAsc(TransactionNature.TRANSFER));
    }

    @Override
    public List<Transaction> findUncategorized() {
        return mapper.toDomain(jpa.findByCategoryIdIsNullAndNatureNotOrderByTransactionDateAscIdAsc(TransactionNature.TRANSFER));
    }

    @Override
    public PagedTransactions search(TransactionFilter filter, int page, int size) {
        Page<TransactionEntity> result = jpa.findAll(toSpecification(filter),
                PageRequest.of(page, size, DEFAULT_SORT));
        return new PagedTransactions(
                mapper.toDomain(result.getContent()),
                result.getTotalElements(),
                page,
                size);
    }

    private static Specification<TransactionEntity> toSpecification(TransactionFilter filter) {
        return (Root<TransactionEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.accountId() != null) {
                predicates.add(cb.equal(root.get("accountId"), filter.accountId()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (Boolean.TRUE.equals(filter.uncategorized())) {
                predicates.add(cb.isNull(root.get("categoryId")));
            }
            if (filter.nature() != null) {
                predicates.add(cb.equal(root.get("nature"), filter.nature()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.to()));
            }
            String queryText = filter.query();
            if (queryText != null && !queryText.isBlank()) {
                String like = "%" + queryText.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("merchant")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
