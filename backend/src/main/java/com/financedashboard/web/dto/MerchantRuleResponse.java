package com.financedashboard.web.dto;

import com.financedashboard.application.MerchantRuleService.RuleDetail;

/** API representation of one merchant-to-category default. */
public record MerchantRuleResponse(
        Long id,
        String merchant,
        Long categoryId,
        String categoryName,
        String color) {

    public static MerchantRuleResponse from(RuleDetail detail) {
        return new MerchantRuleResponse(
                detail.id(), detail.merchant(), detail.categoryId(),
                detail.categoryName(), detail.color());
    }
}
