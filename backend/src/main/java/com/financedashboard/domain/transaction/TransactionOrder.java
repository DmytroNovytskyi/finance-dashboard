package com.financedashboard.domain.transaction;

/** Column and direction used to order the transaction list; id descending breaks ties. */
public record TransactionOrder(TransactionSortField field, boolean ascending) {
}
