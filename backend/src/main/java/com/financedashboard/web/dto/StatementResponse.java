package com.financedashboard.web.dto;

import com.financedashboard.application.StatementService.StatementSummary;
import com.financedashboard.domain.statement.BankStatement;
import java.time.Instant;
import java.time.LocalDate;

/** API representation of one imported {@link BankStatement}. */
public record StatementResponse(
        Long id,
        Long accountId,
        String bank,
        LocalDate periodStart,
        LocalDate periodEnd,
        String fileName,
        Instant importedAt,
        long transactionCount) {

    public static StatementResponse from(StatementSummary summary) {
        BankStatement statement = summary.statement();
        return new StatementResponse(
                statement.getId(),
                statement.getAccountId(),
                statement.getBank(),
                statement.getPeriodStart(),
                statement.getPeriodEnd(),
                statement.getFileName(),
                statement.getImportedAt(),
                summary.transactionCount());
    }
}
