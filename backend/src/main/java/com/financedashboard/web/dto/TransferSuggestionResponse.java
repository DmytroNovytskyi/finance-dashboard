package com.financedashboard.web.dto;

import com.financedashboard.application.TransferSuggestionService.SuggestedTransfer;
import java.math.BigDecimal;
import java.time.LocalDate;

/** API representation of a suggested internal transfer. */
public record TransferSuggestionResponse(
        Long fromTransactionId,
        Long toTransactionId,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        String currency,
        LocalDate fromDate,
        LocalDate toDate,
        String reason) {

    public static TransferSuggestionResponse from(SuggestedTransfer s) {
        return new TransferSuggestionResponse(
                s.fromTransactionId(),
                s.toTransactionId(),
                s.fromAccountId(),
                s.toAccountId(),
                s.amount(),
                s.currency(),
                s.fromDate(),
                s.toDate(),
                s.reason());
    }
}
