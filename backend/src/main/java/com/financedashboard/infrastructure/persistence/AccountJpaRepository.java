package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.account.AccountKind;
import com.financedashboard.infrastructure.persistence.entity.AccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code account} table. */
public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {

    List<AccountEntity> findAllByOrderBySortOrderAscNameAsc();

    List<AccountEntity> findByKindOrderBySortOrderAscNameAsc(AccountKind kind);

    Optional<AccountEntity> findFirstByAccountNumberOrderByIdAsc(String accountNumber);

    Optional<AccountEntity> findFirstByCurrencyOrderBySortOrderAscIdAsc(String currency);
}
