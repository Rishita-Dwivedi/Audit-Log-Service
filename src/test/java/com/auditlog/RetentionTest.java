package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.ErrorResponse;
import com.auditlog.dto.RetentionApplyResponse;
import com.auditlog.dto.VerifyResponse;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/DECISIONS.md ADR-004 (FR-B1): soft-delete via a status flag, must not false-positive on
 * /audit/verify for legitimately archived records.
 */
class RetentionTest extends AbstractApiIntegrationTest {

    @Test
    void archivesRecordsOlderThanTheWindow() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        // windowDays = -1 makes the cutoff one day in the future, so every record just created
        // (recordedAt ~= now) is "older than the window" without needing to wait or backdate.
        RetentionApplyResponse response = post("/audit/retention/apply?windowDays=-1", DEFAULT_TENANT,
                new String[]{Roles.AUDITOR}, null, RetentionApplyResponse.class).getBody();

        assertThat(response.archivedCount()).isEqualTo(2);
    }

    @Test
    void archivalDoesNotFalsePositiveOnVerify() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        post("/audit/retention/apply?windowDays=-1", DEFAULT_TENANT, new String[]{Roles.AUDITOR}, null, RetentionApplyResponse.class);

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isTrue();
        assertThat(verify.recordsChecked()).isEqualTo(2L);
    }

    @Test
    void archivedRecordShowsArchivedStatus() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        post("/audit/retention/apply?windowDays=-1", DEFAULT_TENANT, new String[]{Roles.AUDITOR}, null, RetentionApplyResponse.class);

        AuditEventResponse refetched = get("/audit/events/" + created.id(), AuditEventResponse.class);

        assertThat(refetched.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void reapplyingRetentionIsIdempotent() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        post("/audit/retention/apply?windowDays=-1", DEFAULT_TENANT, new String[]{Roles.AUDITOR}, null, RetentionApplyResponse.class);
        RetentionApplyResponse second = post("/audit/retention/apply?windowDays=-1", DEFAULT_TENANT,
                new String[]{Roles.AUDITOR}, null, RetentionApplyResponse.class).getBody();

        assertThat(second.archivedCount()).isEqualTo(0);
    }

    @Test
    void retentionRequiresAuditorRole() {
        ResponseEntity<ErrorResponse> response = post("/audit/retention/apply", DEFAULT_TENANT,
                new String[]{Roles.USER}, null, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void archivalDoesNotOverwriteRedactedStatus() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        post("/audit/events/" + created.id() + "/redact", DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "test"), AuditEventResponse.class);

        post("/audit/retention/apply?windowDays=-1", DEFAULT_TENANT, new String[]{Roles.AUDITOR}, null, RetentionApplyResponse.class);

        AuditEventResponse refetched = get("/audit/events/" + created.id(), AuditEventResponse.class);
        // Redaction is kept as the displayed status even though the record is also past the
        // retention window -- documented trade-off, docs/DECISIONS.md ADR-004.
        assertThat(refetched.status()).isEqualTo("REDACTED");
    }
}
