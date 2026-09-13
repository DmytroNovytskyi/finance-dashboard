package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.domain.transaction.TransactionOrder;
import com.financedashboard.domain.transaction.TransactionSortField;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import com.financedashboard.infrastructure.persistence.mapper.TransactionMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
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

    private static final TransactionOrder DEFAULT_ORDER =
            new TransactionOrder(TransactionSortField.DATE, false);

    private final TransactionJpaRepository jpa;
    private final TransactionMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

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
    public List<Transaction> findStatistical(LocalDate from, LocalDate to, Collection<Long> accountIds) {
        Specification<TransactionEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.not(root.get("nature").in(TransactionNature.excludedFromStatistics())));
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
    public List<Transaction> findRefunds(LocalDate from, LocalDate to, Collection<Long> accountIds) {
        Specification<TransactionEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("nature"), TransactionNature.REFUND));
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
    public List<Transaction> findByRefundGroupIds(Collection<UUID> refundGroupIds) {
        return mapper.toDomain(jpa.findByRefundGroupIdIn(refundGroupIds));
    }

    @Override
    public boolean existsByStatementId(Long statementId) {
        return jpa.existsByStatementId(statementId);
    }

    @Override
    public boolean existsByAccountId(Long accountId) {
        return jpa.existsByAccountId(accountId);
    }

    @Override
    public List<Transaction> findByStatementId(Long statementId) {
        return mapper.toDomain(jpa.findByStatementId(statementId));
    }

    @Override
    public List<Transaction> findByAccountId(Long accountId) {
        return mapper.toDomain(jpa.findByAccountIdOrderByTransactionDateAscIdAsc(accountId));
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
        for (Long id : ids) {
            TransactionEntity managed = entityManager.find(TransactionEntity.class, id);
            if (managed != null) {
                entityManager.detach(managed);
            }
        }
    }

    @Override
    public List<Transaction> findAll() {
        return mapper.toDomain(jpa.findAll(Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("id"))));
    }

    @Override
    public List<Transaction> findAllStatistical() {
        return mapper.toDomain(jpa.findByNatureNotInOrderByTransactionDateAscIdAsc(
                TransactionNature.excludedFromStatistics()));
    }

    @Override
    public List<Transaction> findUncategorized() {
        return mapper.toDomain(jpa.findByCategoryIdIsNullAndNatureNotInOrderByTransactionDateAscIdAsc(
                TransactionNature.excludedFromStatistics()));
    }

    @Override
    public List<Transaction> findCategorized() {
        return mapper.toDomain(jpa.findByCategoryIdIsNotNullAndNatureNotInOrderByTransactionDateAscIdAsc(
                TransactionNature.excludedFromStatistics()));
    }

    @Override
    public PagedTransactions search(TransactionFilter filter, int page, int size) {
        return search(filter, page, size, DEFAULT_ORDER);
    }

    @Override
    public PagedTransactions search(TransactionFilter filter, int page, int size, TransactionOrder order) {
        Page<TransactionEntity> result = jpa.findAll(toSpecification(filter),
                PageRequest.of(page, size, toSpringSort(order)));
        return new PagedTransactions(
                mapper.toDomain(result.getContent()),
                result.getTotalElements(),
                page,
                size);
    }

    private static Sort toSpringSort(TransactionOrder order) {
        Sort.Direction direction = order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        String path = switch (order.field()) {
            case DATE -> "transactionDate";
            case AMOUNT -> "amount";
            case ACCOUNT -> "account.name";
            case CATEGORY -> "category.name";
        };
        return Sort.by(direction, path).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private static Specification<TransactionEntity> toSpecification(TransactionFilter filter) {
        return (Root<TransactionEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.ids() != null && !filter.ids().isEmpty()) {
                predicates.add(root.get("id").in(filter.ids()));
            }
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
            if (filter.merchant() != null) {
                predicates.add(cb.equal(cb.trim(root.<String>get("merchant")), filter.merchant().trim()));
            }
            if (Boolean.TRUE.equals(filter.withoutMerchant())) {
                Expression<String> merchant = cb.trim(root.<String>get("merchant"));
                predicates.add(cb.or(cb.isNull(root.get("merchant")), cb.equal(merchant, "")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
