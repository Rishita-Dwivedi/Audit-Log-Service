package com.auditlog.security;

/**
 * Per-request holder for the authenticated principal, set by JwtAuthenticationFilter before a
 * request reaches any controller/service and cleared afterward. A ThreadLocal is sufficient
 * here (no reactive/async request handling in this codebase); revisit if that changes.
 */
public final class AuditSecurityContext {

    private static final ThreadLocal<AuthenticatedPrincipal> CURRENT = new ThreadLocal<>();

    private AuditSecurityContext() {
    }

    public static void set(AuthenticatedPrincipal principal) {
        CURRENT.set(principal);
    }

    public static AuthenticatedPrincipal current() {
        AuthenticatedPrincipal principal = CURRENT.get();
        if (principal == null) {
            throw new UnauthorizedException("No authenticated principal in context");
        }
        return principal;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
