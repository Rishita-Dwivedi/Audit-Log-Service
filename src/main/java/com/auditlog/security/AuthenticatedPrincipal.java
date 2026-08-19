package com.auditlog.security;

import java.util.Set;

/**
 * Forward-declared extension point only -- see docs/EVALUATION_CLOSURE_MATRIX.md item 3
 * (SEC-03) and docs/DECISIONS.md ADR-008. Nothing in Phase 1 constructs or consults this
 * type: no authentication or authorization is enforced yet. It exists so that the service
 * signatures needing it later don't require a larger rewrite, not to imply security work is
 * complete.
 */
public record AuthenticatedPrincipal(String subjectId, String tenantId, Set<String> roles) {
}
