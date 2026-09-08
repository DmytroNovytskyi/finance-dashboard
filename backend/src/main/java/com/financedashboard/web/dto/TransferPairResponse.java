package com.financedashboard.web.dto;

import java.util.UUID;

/** API response for a paired internal transfer. */
public record TransferPairResponse(
        UUID transferGroupId,
        TransactionResponse from,
        TransactionResponse to) {
}
