package com.financedashboard.web.dto;

import com.financedashboard.domain.transaction.Transaction;
import com.financedashboard.domain.transaction.TransactionNature;
import java.math.BigDecimal;
import java.time.LocalDate;

/** API representation of a {@link Transaction}. */
public record TransactionResponse(
        Long id,
        Long accountId,
        Long statementId,
        LocalDate transactionDate,
        BigDecimal amount,
        String currency,
        TransactionNature nature,
        String description,
        String merchant,
        Long categoryId) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getStatementId(),
                transaction.getTransactionDate(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getNature(),
                transaction.getDescription(),
                transaction.getMerchant(),
                transaction.getCategoryId());
    }
}
