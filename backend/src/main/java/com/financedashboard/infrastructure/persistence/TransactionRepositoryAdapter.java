package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.domain.transaction.TransactionOrder;
import com.financedashboard.domain.transaction.TransactionSortField;
import com.financedashboard.infrastructure.persistence.entity.TransactionAmountEntity;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import com.financedashboard.infrastructure.persistence.mapper.TransactionMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${finance.base-currency:PLN}")
    private String baseCurrency;

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
        if (order.field() == TransactionSortField.AMOUNT) {
            return searchByBaseCurrencyWorth(filter, page, size, order);
        }
        Page<TransactionEntity> result = jpa.findAll(toSpecification(filter),
                PageRequest.of(page, size, toSpringSort(order)));
        return new PagedTransactions(
                mapper.toDomain(result.getContent()),
                result.getTotalElements(),
                page,
                size);
    }

    /**
     * Orders by what each row was worth in the base currency rather than by its native amount, so
     * that rows in different currencies are compared on one scale. The worth lives in
     * {@code transaction_amount}, which {@link TransactionEntity} holds no association to and which
     * a {@link Sort} cannot narrow to a single currency, so the ordering is a correlated subquery and
     * paging is driven by its own count query. A row with no stored base-currency value keeps its
     * place in the list — the statistics skip such rows, the list must not — and is ranked last in
     * both directions.
     */
    private PagedTransactions searchByBaseCurrencyWorth(TransactionFilter filter, int page, int size,
                                                        TransactionOrder order) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TransactionEntity> query = cb.createQuery(TransactionEntity.class);
        Root<TransactionEntity> root = query.from(TransactionEntity.class);
        query.where(toSpecification(filter).toPredicate(root, query, cb));

        Expression<BigDecimal> worth = baseCurrencyWorth(cb, query, root);
        Expression<Integer> unvalued = cb.<Integer>selectCase().when(cb.isNull(worth), 1).otherwise(0);
        query.orderBy(
                cb.asc(unvalued),
                order.ascending() ? cb.asc(worth) : cb.desc(worth),
                cb.desc(root.get("id")));

        List<TransactionEntity> content = entityManager.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
        return new PagedTransactions(
                mapper.toDomain(content),
                countMatching(filter, cb),
                page,
                size);
    }

    /**
     * The stored amount for the base currency, as a subquery correlated to the outer row; null when
     * the row has no stored value for that currency.
     */
    private Expression<BigDecimal> baseCurrencyWorth(CriteriaBuilder cb, CriteriaQuery<?> query,
                                                     Root<TransactionEntity> root) {
        Subquery<BigDecimal> worth = query.subquery(BigDecimal.class);
        Root<TransactionAmountEntity> stored = worth.from(TransactionAmountEntity.class);
        worth.select(stored.<BigDecimal>get("amount"))
                .where(cb.equal(stored.<Long>get("transactionId"), root.<Long>get("id")),
                        cb.equal(stored.<String>get("currency"),
                                baseCurrency.trim().toUpperCase(Locale.ROOT)));
        return worth;
    }

    private long countMatching(TransactionFilter filter, CriteriaBuilder cb) {
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<TransactionEntity> root = count.from(TransactionEntity.class);
        count.select(cb.count(root));
        count.where(toSpecification(filter).toPredicate(root, count, cb));
        return entityManager.createQuery(count).getSingleResult();
    }

    private static Sort toSpringSort(TransactionOrder order) {
        Sort.Direction direction = order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        String path = switch (order.field()) {
            case DATE -> "transactionDate";
            case ACCOUNT -> "account.name";
            case CATEGORY -> "category.name";
            case AMOUNT -> throw new IllegalArgumentException(
                    "Amount is ordered by base-currency worth, not by a column");
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
