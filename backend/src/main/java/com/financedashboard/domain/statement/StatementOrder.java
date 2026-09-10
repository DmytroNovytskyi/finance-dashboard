package com.financedashboard.domain.statement;

/** The ordering of the statement list: which column, and in which direction. */
public record StatementOrder(StatementSortField field, boolean ascending) {
}
