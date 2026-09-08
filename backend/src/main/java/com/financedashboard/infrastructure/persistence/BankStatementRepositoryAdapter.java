package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.BankStatementRepository;
import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.infrastructure.persistence.mapper.BankStatementMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link BankStatementRepository}. */
@Component
@RequiredArgsConstructor
public class BankStatementRepositoryAdapter implements BankStatementRepository {

    private final BankStatementJpaRepository jpa;
    private final BankStatementMapper mapper;

    @Override
    public BankStatement save(BankStatement statement) {
        return mapper.toDomain(jpa.save(mapper.toEntity(statement)));
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
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
