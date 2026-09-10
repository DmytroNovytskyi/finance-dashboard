package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.domain.statement.StatementOrder;
import com.financedashboard.domain.statement.StatementSortField;
import com.financedashboard.infrastructure.persistence.entity.BankStatementEntity;
import com.financedashboard.infrastructure.persistence.mapper.BankStatementMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link BankStatementRepository}. */
@Component
@RequiredArgsConstructor
public class BankStatementRepositoryAdapter implements BankStatementRepository {

    private static final StatementOrder DEFAULT_ORDER =
            new StatementOrder(StatementSortField.IMPORTED, false);

    private final BankStatementJpaRepository jpa;
    private final BankStatementMapper mapper;

    @Override
    public BankStatement save(BankStatement statement) {
        return mapper.toDomain(jpa.save(mapper.toEntity(statement)));
    }

    @Override
    public List<BankStatement> findAll(StatementOrder order, Long accountId) {
        Specification<BankStatementEntity> spec = (root, query, cb) -> accountId == null
                ? cb.conjunction()
                : cb.equal(root.get("accountId"), accountId);
        return mapper.toDomain(jpa.findAll(spec, toSpringSort(order == null ? DEFAULT_ORDER : order)));
    }

    private static Sort toSpringSort(StatementOrder order) {
        Sort.Direction direction = order.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        String path = switch (order.field()) {
            case IMPORTED -> "importedAt";
            case FILE -> "fileName";
            case ACCOUNT -> "account.name";
            case PERIOD -> "periodStart";
        };
        return Sort.by(direction, path).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Override
    public List<BankStatement> findAllByOrderByPeriodStartAsc() {
        return mapper.toDomain(jpa.findAllByOrderByPeriodStartAsc());
    }

    @Override
    public List<BankStatement> findByAccountId(Long accountId) {
        return mapper.toDomain(jpa.findByAccountIdOrderByImportedAtDesc(accountId));
    }

    @Override
    public Optional<BankStatement> findById(Long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByFileHash(String fileHash) {
        return jpa.existsByFileHash(fileHash);
    }

    @Override
    public boolean existsByAccountId(Long accountId) {
        return jpa.existsByAccountId(accountId);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
