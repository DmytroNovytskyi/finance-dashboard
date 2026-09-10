package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request body for pairing a purchase with its refund. */
public record PairRefundRequest(
        @NotNull @Positive Long purchaseTransactionId,
        @NotNull @Positive Long refundTransactionId) {
}
