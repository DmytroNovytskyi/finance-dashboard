package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.BankStatementEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code bank_statement} table. */
public interface BankStatementJpaRepository extends JpaRepository<BankStatementEntity, Long> {

    boolean existsByFileHash(String fileHash);

    Optional<BankStatementEntity> findByFileHash(String fileHash);
}
