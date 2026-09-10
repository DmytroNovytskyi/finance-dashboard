package com.financedashboard.domain.category;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A flat spending group. Deleting a category leaves its transactions uncategorized; system
 * categories (like {@code Transfer} and {@code Refund}) are reserved and cannot be deleted. Each
 * carries a {@code systemKey} so it can be looked up unambiguously — by name would break as soon
 * as the user renames one.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Category {

    /** Key of the reserved category that internal transfer legs carry. */
    public static final String SYSTEM_KEY_TRANSFER = "TRANSFER";

    /** Key of the reserved category that both legs of a refund carry. */
    public static final String SYSTEM_KEY_REFUND = "REFUND";

    private final Long id;
    private final String name;
    private final String color;
    private final int sortOrder;
    private final boolean system;
    private final String systemKey;
}
