package com.financedashboard.infrastructure.persistence.mapper;

import com.financedashboard.domain.account.Account;
import com.financedashboard.infrastructure.persistence.entity.AccountEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps between the {@link Account} aggregate and its JPA entity. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountMapper {

    Account toDomain(AccountEntity entity);

    AccountEntity toEntity(Account account);

    List<Account> toDomain(List<AccountEntity> entities);
}
