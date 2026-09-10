package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Request body for pairing a purchase with the one or more credits that reverse it. */
public record PairRefundRequest(
        @NotNull @Positive Long purchaseTransactionId,
        @NotEmpty List<@NotNull @Positive Long> refundTransactionIds) {
}
