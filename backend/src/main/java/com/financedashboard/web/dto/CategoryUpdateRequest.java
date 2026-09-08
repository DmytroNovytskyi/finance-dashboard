package com.financedashboard.web.dto;

/** Partial update for a category; null fields leave the value unchanged. */
public record CategoryUpdateRequest(
        String name,
        String color) {
}
