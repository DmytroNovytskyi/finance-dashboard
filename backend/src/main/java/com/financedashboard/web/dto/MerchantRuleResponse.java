package com.financedashboard.web.dto;

import com.financedashboard.application.MerchantRuleService.RuleDetail;
import com.financedashboard.domain.merchant_rule.MatchType;

/** API representation of one merchant-to-category default. */
public record MerchantRuleResponse(
        Long id,
        String merchant,
        MatchType matchType,
        Long categoryId,
        String categoryName,
        String color) {

    public static MerchantRuleResponse from(RuleDetail detail) {
        return new MerchantRuleResponse(
                detail.id(), detail.merchant(), detail.matchType(), detail.categoryId(),
                detail.categoryName(), detail.color());
    }
}
