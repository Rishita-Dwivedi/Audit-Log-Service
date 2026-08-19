package com.auditlog.security;

/** No authenticated principal present where one was required. Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
