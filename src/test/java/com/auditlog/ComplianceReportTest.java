package com.auditlog;

import com.auditlog.dto.AuditEventPageResponse;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.ErrorResponse;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/scenario-c.md, docs/REQUIREMENTS.md FR-C1/FR-C2.
 */
class ComplianceReportTest extends AbstractApiIntegrationTest {

    private static final String RANGE = "&from=2026-01-01T00:00:00Z&to=2026-12-31T00:00:00Z";

    @Test
    void requiresComplianceOfficerRole() {
        // A plain AUDITOR is not sufficient -- the roles are genuinely distinct, not just
        // "any elevated role" (docs/scenario-c.md).
        ResponseEntity<ErrorResponse> asUser = get("/audit/compliance-report?" + RANGE.substring(1), DEFAULT_TENANT,
                new String[]{Roles.USER}, ErrorResponse.class);
        ResponseEntity<ErrorResponse> asAuditor = get("/audit/compliance-report?" + RANGE.substring(1), DEFAULT_TENANT,
                new String[]{Roles.AUDITOR}, ErrorResponse.class);

        assertThat(asUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asAuditor.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requiresBothFromAndTo() {
        ResponseEntity<ErrorResponse> missingBoth = get("/audit/compliance-report", DEFAULT_TENANT,
                new String[]{Roles.COMPLIANCE_OFFICER}, ErrorResponse.class);
        ResponseEntity<ErrorResponse> missingTo = get("/audit/compliance-report?from=2026-01-01T00:00:00Z",
                DEFAULT_TENANT, new String[]{Roles.COMPLIANCE_OFFICER}, ErrorResponse.class);

        assertThat(missingBoth.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingTo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyReturnsAllowlistedResourceTypes() {
        createEvent("RECORD_VIEWED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "SESSION", "sess-1", Map.of(), OffsetDateTime.parse("2026-06-01T00:01:00Z"));

        AuditEventPageResponse report = get("/audit/compliance-report?" + RANGE.substring(1), DEFAULT_TENANT,
                new String[]{Roles.COMPLIANCE_OFFICER}, AuditEventPageResponse.class).getBody();

        assertThat(report.items()).hasSize(1);
        assertThat(report.items().get(0).resourceType()).isEqualTo("ACCOUNT");
    }

    @Test
    void reportsAcrossAllTenantsWhenNoTenantIdGiven() {
        createEvent(DEFAULT_TENANT, "RECORD_VIEWED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        createEvent(OTHER_TENANT, "RECORD_VIEWED", "user-2", "ACCOUNT", "acct-2", Map.of(), OffsetDateTime.parse("2026-06-01T00:01:00Z"));

        AuditEventPageResponse report = get("/audit/compliance-report?" + RANGE.substring(1), DEFAULT_TENANT,
                new String[]{Roles.COMPLIANCE_OFFICER}, AuditEventPageResponse.class).getBody();

        assertThat(report.items()).hasSize(2);
    }

    @Test
    void reportCanBeNarrowedToOneTenant() {
        createEvent(DEFAULT_TENANT, "RECORD_VIEWED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        createEvent(OTHER_TENANT, "RECORD_VIEWED", "user-2", "ACCOUNT", "acct-2", Map.of(), OffsetDateTime.parse("2026-06-01T00:01:00Z"));

        AuditEventPageResponse report = get("/audit/compliance-report?tenantId=" + OTHER_TENANT + RANGE, DEFAULT_TENANT,
                new String[]{Roles.COMPLIANCE_OFFICER}, AuditEventPageResponse.class).getBody();

        assertThat(report.items()).hasSize(1);
        assertThat(report.items().get(0).tenantId()).isEqualTo(OTHER_TENANT);
    }

    @Test
    void reportRespectsExistingRedactionRatherThanBypassingIt() {
        AuditEventResponse created = createEvent("RECORD_VIEWED", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-06-01T00:00:00Z"));
        post("/audit/events/" + created.id() + "/redact", DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "test"), AuditEventResponse.class);

        AuditEventPageResponse report = get("/audit/compliance-report?" + RANGE.substring(1), DEFAULT_TENANT,
                new String[]{Roles.COMPLIANCE_OFFICER}, AuditEventPageResponse.class).getBody();

        assertThat(report.items().get(0).payload().get("accountNumber").asText()).startsWith("[REDACTED:sha256:");
    }

    @Test
    void excludesEventsOutsideTheRequestedTimeRange() {
        createEvent("RECORD_VIEWED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        createEvent("RECORD_VIEWED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"));

        AuditEventPageResponse report = get("/audit/compliance-report?" + RANGE.substring(1), DEFAULT_TENANT,
                new String[]{Roles.COMPLIANCE_OFFICER}, AuditEventPageResponse.class).getBody();

        assertThat(report.items()).hasSize(1);
    }
}
