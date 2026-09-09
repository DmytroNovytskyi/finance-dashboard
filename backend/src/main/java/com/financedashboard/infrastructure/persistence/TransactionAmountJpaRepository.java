package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.TransactionAmountEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code transaction_amount} table. */
public interface TransactionAmountJpaRepository
        extends JpaRepository<TransactionAmountEntity, TransactionAmountEntity.TransactionAmountId> {

    List<TransactionAmountEntity> findByTransactionIdInAndCurrency(Collection<Long> transactionIds, String currency);

    List<TransactionAmountEntity> findByTransactionIdIn(Collection<Long> transactionIds);
}
