package com.financedashboard.web.dto;

import com.financedashboard.application.RefundSuggestionService.SuggestedRefund;
import java.math.BigDecimal;
import java.time.LocalDate;

/** API representation of a suggested purchase-and-refund pair. */
public record RefundSuggestionResponse(
        Long purchaseTransactionId,
        Long refundTransactionId,
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
                suggestion.refundTransactionId(),
                suggestion.accountId(),
                suggestion.amount(),
                suggestion.currency(),
                suggestion.merchant(),
                suggestion.purchaseDate(),
                suggestion.refundDate(),
                suggestion.reason());
    }
}
