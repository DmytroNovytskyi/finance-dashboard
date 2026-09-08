package com.financedashboard.web.dto;

import com.financedashboard.application.StatementImportService.StatementImportResult;

/** API response for a statement import. */
public record StatementImportResponse(
        Long statementId,
        boolean alreadyImported,
        int imported,
        int skipped) {

    public static StatementImportResponse from(StatementImportResult result) {
        return new StatementImportResponse(
                result.statementId(),
                result.alreadyImported(),
                result.imported(),
                result.skipped());
    }
}
