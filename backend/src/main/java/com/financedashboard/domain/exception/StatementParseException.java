package com.financedashboard.domain.exception;

/** Thrown when a recognized document cannot be parsed into transactions. */
public class StatementParseException extends RuntimeException {

    public StatementParseException(String message) {
        super(message);
    }

    public StatementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
