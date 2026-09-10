package com.financedashboard.web.dto;

import java.util.UUID;

/** API response for a paired purchase and refund. */
public record RefundPairResponse(
        UUID refundGroupId,
        TransactionResponse purchase,
        TransactionResponse refund) {
}
