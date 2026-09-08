package com.financedashboard.application.exception;

/** Thrown when a requested aggregate does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
