package com.financedashboard.infrastructure.persistence.mapper;

import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.infrastructure.persistence.entity.TransactionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps between the {@link Transaction} aggregate and its JPA entity. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TransactionMapper {

    Transaction toDomain(TransactionEntity entity);

    TransactionEntity toEntity(Transaction transaction);

    List<Transaction> toDomain(List<TransactionEntity> entities);
}
