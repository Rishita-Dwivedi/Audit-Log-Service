package com.auditlog;

import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.ErrorResponse;
import com.auditlog.dto.VerifyResponse;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/EVALUATION_CLOSURE_MATRIX.md items 3 (SEC-03) and 21 (TEST-04): cross-tenant access
 * must be denied and must not leak whether another tenant's data exists (BOLA/IDOR
 * prevention -- assignment Section 12).
 */
class TenantIsolationTest extends AbstractApiIntegrationTest {

    @Test
    void queryNeverReturnsAnotherTenantsRecords() {
        createEvent(DEFAULT_TENANT, "USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-2", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        AuditEventPageResponse response = get(
                "/audit/events", DEFAULT_TENANT, new String[]{Roles.USER}, AuditEventPageResponse.class).getBody();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).tenantId()).isEqualTo(DEFAULT_TENANT);
    }

    @Test
    void queryIgnoresAttemptToRequestAnotherTenantAsNonAuditor() {
        createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-2", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        // Caller authenticated for DEFAULT_TENANT tries to request OTHER_TENANT's data via the
        // tenantId query param -- must be silently ignored, not honored and not a 403 (a 403
        // would confirm OTHER_TENANT's data exists, which is itself a leak).
        AuditEventPageResponse response = get("/audit/events?tenantId=" + OTHER_TENANT, DEFAULT_TENANT,
                new String[]{Roles.USER}, AuditEventPageResponse.class).getBody();

        assertThat(response.items()).isEmpty();
    }

    @Test
    void fetchByIdAcrossTenantsReturns404NotForbidden() {
        AuditEventResponse record = createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-2", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<ErrorResponse> response = get(
                "/audit/events/" + record.id(), DEFAULT_TENANT, new String[]{Roles.USER}, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void writeIsAlwaysScopedToCallersOwnTenant() {
        // tenantId comes only from the JWT -- AuditEventCreateRequest has no tenantId field to
        // even attempt an override with (docs/DECISIONS.md).
        AuditEventResponse record = createEvent(DEFAULT_TENANT, "USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        assertThat(record.tenantId()).isEqualTo(DEFAULT_TENANT);
    }

    @Test
    void verifyRequiresAuditorRole() {
        ResponseEntity<ErrorResponse> response = get(
                "/audit/verify", DEFAULT_TENANT, new String[]{Roles.USER}, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void verifySucceedsForAuditorAcrossTenants() {
        createEvent(DEFAULT_TENANT, "USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-2", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isTrue();
        assertThat(verify.recordsChecked()).isEqualTo(2L);
    }

    @Test
    void auditorCanQueryAnySpecificTenant() {
        createEvent(DEFAULT_TENANT, "USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-2", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        AuditEventPageResponse response = get("/audit/events?tenantId=" + OTHER_TENANT, DEFAULT_TENANT,
                new String[]{Roles.AUDITOR}, AuditEventPageResponse.class).getBody();

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).tenantId()).isEqualTo(OTHER_TENANT);
    }
}
