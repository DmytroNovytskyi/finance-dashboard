package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.port.TransactionAmountRepository;
import com.financedashboard.domain.transaction.TransactionAmount;
import com.financedashboard.infrastructure.persistence.entity.TransactionAmountEntity;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link TransactionAmountRepository}. */
@Component
@RequiredArgsConstructor
public class TransactionAmountRepositoryAdapter implements TransactionAmountRepository {

    private final TransactionAmountJpaRepository jpa;

    @Override
    public void saveAll(Collection<TransactionAmount> rows) {
        jpa.saveAll(rows.stream().map(this::toEntity).toList());
    }

    @Override
    public Map<Long, BigDecimal> findAmountsByCurrency(Collection<Long> transactionIds, String currency) {
        Map<Long, BigDecimal> amounts = new HashMap<>();
        for (TransactionAmountEntity entity
                : jpa.findByTransactionIdInAndCurrency(transactionIds, currency)) {
            amounts.put(entity.getTransactionId(), entity.getAmount());
        }
        return amounts;
    }

    @Override
    public List<TransactionAmount> findByTransactionIds(Collection<Long> transactionIds) {
        return jpa.findByTransactionIdIn(transactionIds).stream().map(this::toDomain).toList();
    }

    private TransactionAmountEntity toEntity(TransactionAmount amount) {
        TransactionAmountEntity entity = new TransactionAmountEntity();
        entity.setTransactionId(amount.transactionId());
        entity.setCurrency(amount.currency());
        entity.setAmount(amount.amount());
        return entity;
    }

    private TransactionAmount toDomain(TransactionAmountEntity entity) {
        return new TransactionAmount(entity.getTransactionId(), entity.getCurrency(), entity.getAmount());
    }
}
