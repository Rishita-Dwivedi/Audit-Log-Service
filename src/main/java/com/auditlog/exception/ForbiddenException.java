package com.auditlog.exception;

/** Authenticated, but not permitted to perform this operation (e.g., wrong role, wrong tenant). Maps to HTTP 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
