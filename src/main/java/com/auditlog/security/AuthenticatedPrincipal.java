package com.auditlog.security;

import java.util.Set;

/**
 * Populated by JwtAuthenticationFilter from a validated JWT's claims (sub -> subjectId,
 * tenantId claim, roles claim) and made available per-request via AuditSecurityContext.
 * See docs/DECISIONS.md for the authentication/authorization model this supports.
 */
public record AuthenticatedPrincipal(String subjectId, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
