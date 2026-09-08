package com.financedashboard.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for creating a category. */
public record CategoryCreateRequest(
        @NotBlank(message = "name must not be blank")
        String name,

        String color,

        Integer sortOrder) {
}
