package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.BankStatementEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code bank_statement} table. */
public interface BankStatementJpaRepository extends JpaRepository<BankStatementEntity, Long> {

    List<BankStatementEntity> findAllByOrderByImportedAtDesc();

    List<BankStatementEntity> findByAccountIdOrderByImportedAtDesc(Long accountId);

    boolean existsByFileHash(String fileHash);

    boolean existsByAccountId(Long accountId);

    Optional<BankStatementEntity> findByFileHash(String fileHash);
}
