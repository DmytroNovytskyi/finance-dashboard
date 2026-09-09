package com.financedashboard.domain.exception;

/**
 * Thrown when a statement cannot be imported because an FX rate for one of its transaction dates
 * cannot be resolved within the configured lookback window (or the rate source is unavailable).
 */
public class StatementFxRateException extends RuntimeException {

    public StatementFxRateException(String message) {
        super(message);
    }

    public StatementFxRateException(String message, Throwable cause) {
        super(message, cause);
    }
}
