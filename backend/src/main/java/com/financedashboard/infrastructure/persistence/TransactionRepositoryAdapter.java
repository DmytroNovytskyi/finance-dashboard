package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.TransactionRepository;
import com.financedashboard.domain.transaction.PagedTransactions;
import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionFilter;
import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import com.financedashboard.infrastructure.persistence.mapper.TransactionMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
