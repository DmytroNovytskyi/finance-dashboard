package com.financedashboard.infrastructure.persistence;

import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.domain.port.MerchantRuleRepository;
import com.financedashboard.infrastructure.persistence.mapper.MerchantRuleMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Spring Data implementation of {@link MerchantRuleRepository}. */
@Component
@RequiredArgsConstructor
public class MerchantRuleRepositoryAdapter implements MerchantRuleRepository {

    private final MerchantRuleJpaRepository jpa;
    private final MerchantRuleMapper mapper;

    @Override
    public List<MerchantRule> findAll() {
        return mapper.toDomain(jpa.findAll());
    }

    @Override
    public MerchantRule save(MerchantRule rule) {
        return mapper.toDomain(jpa.save(mapper.toEntity(rule)));
    }

    @Override
    public boolean existsByMerchant(String merchant) {
        return jpa.existsByMerchant(merchant);
    }

    @Override
    public boolean existsById(Long id) {
        return jpa.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
