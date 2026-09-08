package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request body for pairing two transactions as one internal transfer. */
public record PairTransferRequest(
        @NotNull @Positive Long fromTransactionId,
        @NotNull @Positive Long toTransactionId) {
}
