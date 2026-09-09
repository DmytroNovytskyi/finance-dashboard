package com.financedashboard.domain.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A flat spending group. Deleting a category leaves its transactions uncategorized; system
 * categories (like {@code Transfer}) are reserved and cannot be deleted.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Category {

    private final Long id;
    private final String name;
    private final String color;
    private final int sortOrder;
    private final boolean system;
}
