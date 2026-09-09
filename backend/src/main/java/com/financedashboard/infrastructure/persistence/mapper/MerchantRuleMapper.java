package com.financedashboard.infrastructure.persistence.mapper;

import com.financedashboard.domain.merchant_rule.MerchantRule;
import com.financedashboard.infrastructure.persistence.entity.MerchantRuleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/** Maps between the {@link MerchantRule} aggregate and its JPA entity. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MerchantRuleMapper {

    MerchantRule toDomain(MerchantRuleEntity entity);

    MerchantRuleEntity toEntity(MerchantRule rule);

    List<MerchantRule> toDomain(List<MerchantRuleEntity> entities);
}
