package com.financedashboard.web.dto;

import java.util.List;

/** A page of results returned by list endpoints. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
