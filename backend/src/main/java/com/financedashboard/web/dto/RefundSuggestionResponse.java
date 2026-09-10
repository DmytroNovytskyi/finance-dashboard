package com.financedashboard.web.dto;

import com.financedashboard.application.RefundSuggestionService.SuggestedRefund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** API representation of a suggested reversal: one purchase and the credits that gave it back. */
public record RefundSuggestionResponse(
        Long purchaseTransactionId,
        List<Long> refundTransactionIds,
        Long accountId,
        BigDecimal amount,
        String currency,
        String merchant,
        LocalDate purchaseDate,
        LocalDate refundDate,
        String reason) {

    public static RefundSuggestionResponse from(SuggestedRefund suggestion) {
        return new RefundSuggestionResponse(
                suggestion.purchaseTransactionId(),
                suggestion.refundTransactionIds(),
                suggestion.accountId(),
                suggestion.amount(),
                suggestion.currency(),
                suggestion.merchant(),
                suggestion.purchaseDate(),
                suggestion.refundDate(),
                suggestion.reason());
    }
}
