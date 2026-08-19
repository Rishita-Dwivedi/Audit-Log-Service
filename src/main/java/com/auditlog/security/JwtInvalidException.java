package com.auditlog.security;

/** Missing, malformed, forged, expired, or wrong-issuer/audience token. Maps to HTTP 401. */
public class JwtInvalidException extends RuntimeException {

    public JwtInvalidException(String message) {
        super(message);
    }

    public JwtInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
