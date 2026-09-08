package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Request body for bulk category assignment; a null categoryId clears the category. */
public record CategorizeRequest(
        @NotEmpty List<Long> transactionIds,
        Long categoryId) {
}
