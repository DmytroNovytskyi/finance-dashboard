package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data repository for the {@code transaction} table. */
public interface TransactionJpaRepository
        extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {

    List<TransactionEntity> findByAccountIdAndDedupHashIn(Long accountId, Collection<String> dedupHashes);

    List<TransactionEntity> findByTransferGroupIdIn(Collection<UUID> transferGroupIds);

    List<TransactionEntity> findByNatureNotOrderByTransactionDateAscIdAsc(TransactionNature nature);

    boolean existsByStatementId(Long statementId);
}
