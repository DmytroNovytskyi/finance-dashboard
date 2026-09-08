package com.financedashboard.web.exception;

import java.time.LocalDateTime;

/** Standard error body returned by the REST API. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {
}
