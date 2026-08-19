package com.auditlog.exception;

/** A caller-fixable request problem that isn't a Bean Validation failure. Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
