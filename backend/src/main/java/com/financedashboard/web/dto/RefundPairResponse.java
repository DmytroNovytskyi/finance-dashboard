package com.financedashboard.web.dto;

import java.util.List;
import java.util.UUID;

/** API response for a linked refund: the group id every leg shares, and the stored legs. */
public record RefundPairResponse(
        UUID refundGroupId,
        List<TransactionResponse> transactions) {
}
