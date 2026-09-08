package com.financedashboard.domain.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** A flat spending group. Deleting a category leaves its transactions uncategorized. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Category {

    private final Long id;
    private final String name;
    private final String color;
    private final int sortOrder;
}
