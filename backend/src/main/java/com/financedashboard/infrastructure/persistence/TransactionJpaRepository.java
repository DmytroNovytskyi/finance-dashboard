package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.transaction.TransactionNature;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for the {@code transaction} table. */
public interface TransactionJpaRepository
        extends JpaRepository<TransactionEntity, Long>, JpaSpecificationExecutor<TransactionEntity> {

    List<TransactionEntity> findByAccountIdAndDedupHashIn(Long accountId, Collection<String> dedupHashes);

    List<TransactionEntity> findByTransferGroupIdIn(Collection<UUID> transferGroupIds);

    List<TransactionEntity> findByRefundGroupIdIn(Collection<UUID> refundGroupIds);

    List<TransactionEntity> findByNatureNotInOrderByTransactionDateAscIdAsc(Collection<TransactionNature> natures);

    List<TransactionEntity> findByCategoryIdIsNullAndNatureNotInOrderByTransactionDateAscIdAsc(Collection<TransactionNature> natures);

    List<TransactionEntity> findByCategoryIdIsNotNullAndNatureNotInOrderByTransactionDateAscIdAsc(Collection<TransactionNature> natures);

    List<TransactionEntity> findByStatementId(Long statementId);

    List<TransactionEntity> findByAccountIdOrderByTransactionDateAscIdAsc(Long accountId);

    boolean existsByStatementId(Long statementId);

    boolean existsByAccountId(Long accountId);

    @Query("select t.statementId as statementId, count(t) as transactionCount "
            + "from TransactionEntity t where t.statementId in :statementIds group by t.statementId")
    List<StatementTransactionCount> countByStatementIdIn(@Param("statementIds") Collection<Long> statementIds);

    /** Row count of stored transactions per statement, from a grouped query. */
    interface StatementTransactionCount {
        Long getStatementId();

        Long getTransactionCount();
    }
}
