package com.auditlog.security;

/**
 * Forward-declared extension point only -- see docs/EVALUATION_CLOSURE_MATRIX.md item 3
 * (SEC-03). No implementation exists and nothing calls this interface in Phase 1. A stub
 * implementation is deliberately NOT provided here: a no-op or always-true implementation
 * would misrepresent authorization as enforced when it is not. Query, redaction, export, and
 * compliance endpoints do not check this in Phase 1 -- that is a known, documented gap
 * (docs/SECURITY.md), not an oversight.
 */
public interface ResourceAuthorization {
    boolean isAuthorized(AuthenticatedPrincipal principal, String resourceType, String resourceId);
}
