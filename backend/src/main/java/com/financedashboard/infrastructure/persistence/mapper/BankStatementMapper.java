package com.financedashboard.infrastructure.persistence.mapper;

import com.financedashboard.domain.statement.BankStatement;
import com.financedashboard.infrastructure.persistence.entity.BankStatementEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps between the {@link BankStatement} aggregate and its JPA entity. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BankStatementMapper {

    BankStatement toDomain(BankStatementEntity entity);

    BankStatementEntity toEntity(BankStatement statement);
}
