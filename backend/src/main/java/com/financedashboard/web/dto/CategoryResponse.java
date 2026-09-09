package com.financedashboard.web.dto;

import com.financedashboard.domain.category.Category;

/** API representation of a {@link Category}. */
public record CategoryResponse(
        Long id,
        String name,
        String color,
        int sortOrder,
        boolean system) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getSortOrder(),
                category.isSystem());
    }
}
