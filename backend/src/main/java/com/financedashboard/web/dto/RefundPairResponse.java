package com.financedashboard.web.dto;

import java.util.List;
import java.util.UUID;

/** API response for a paired purchase and the credit or credits that reversed it. */
public record RefundPairResponse(
        UUID refundGroupId,
        TransactionResponse purchase,
        List<TransactionResponse> refunds) {
}
