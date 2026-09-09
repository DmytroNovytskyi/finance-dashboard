package com.financedashboard.web.dto;

/** API response for clearing all merchant defaults: rules dropped and transactions reverted. */
public record ClearDefaultsResponse(int rulesRemoved, int transactionsUncategorized) {
}
