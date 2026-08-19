package com.auditlog.security;

/**
 * Role constants used in JWT `roles` claims and authorization checks throughout the service.
 */
public final class Roles {

    /** Default role: can write events and read/query only their own tenant's data. */
    public static final String USER = "ROLE_USER";

    /** Cross-tenant read access; the only role permitted to call GET /audit/verify. */
    public static final String AUDITOR = "ROLE_AUDITOR";

    /** Scenario C: permitted to call the compliance-reporting endpoint. */
    public static final String COMPLIANCE_OFFICER = "ROLE_COMPLIANCE_OFFICER";

    private Roles() {
    }
}
