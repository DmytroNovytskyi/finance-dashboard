package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data repository for the {@code transaction} table. */
public interface TransactionJpaRepository
        extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {
}
