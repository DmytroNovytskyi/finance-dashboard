package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.AccountEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code account} table. */
public interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {

    List<AccountEntity> findAllByOrderBySortOrderAscNameAsc();
}
