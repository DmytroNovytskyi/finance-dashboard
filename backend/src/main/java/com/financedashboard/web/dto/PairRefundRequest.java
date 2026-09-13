package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request body for linking transactions as one refund. The ids are a plain set of legs with no role
 * attached: a refund has no purchase side, so nothing here says which row reversed which.
 */
public record PairRefundRequest(
        @NotEmpty @Size(min = 2) List<@NotNull @Positive Long> transactionIds) {
}
