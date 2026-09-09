package com.financedashboard.infrastructure.persistence;

import com.financedashboard.infrastructure.persistence.entity.MerchantRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@code merchant_rule} table. */
public interface MerchantRuleJpaRepository extends JpaRepository<MerchantRuleEntity, Long> {

    boolean existsByMerchant(String merchant);
}
