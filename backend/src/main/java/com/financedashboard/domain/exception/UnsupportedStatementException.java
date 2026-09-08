package com.financedashboard.domain.exception;

/** Thrown when no parser recognizes an uploaded document. */
public class UnsupportedStatementException extends RuntimeException {

    public UnsupportedStatementException(String message) {
        super(message);
    }
}
