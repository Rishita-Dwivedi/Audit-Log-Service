package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.ErrorResponse;
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
 * docs/DECISIONS.md ADR-003: redaction must not invalidate the hash chain, and tampering must
 * still be detectable both before and after redaction.
 */
class RedactionTest extends AbstractApiIntegrationTest {

    @Test
    void redactingAFieldReplacesItWithATombstoneAndSetsStatus() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345", "note", "hello"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<AuditEventResponse> response = post("/audit/events/" + created.id() + "/redact",
                DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "GDPR erasure request"),
                AuditEventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuditEventResponse redacted = response.getBody();
        assertThat(redacted.status()).isEqualTo("REDACTED");
        assertThat(redacted.redactedFields()).containsExactly("accountNumber");
        assertThat(redacted.payload().get("accountNumber").asText()).startsWith("[REDACTED:sha256:");
        assertThat(redacted.payload().get("note").asText()).isEqualTo("hello");
    }

    @Test
    void verificationStillSucceedsAfterALegitimateRedaction() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        post("/audit/events/" + created.id() + "/redact", DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "test"), AuditEventResponse.class);

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isTrue();
    }

    @Test
    void verifyDetectsRawPayloadTamperedWithoutUpdatingCommitment() {
        // The gap record_hash alone can't catch: record_hash is computed from
        // field_commitments, not the raw payload (docs/DECISIONS.md ADR-003), so a direct
        // tamper of the raw value alone leaves record_hash unchanged. Only the field-commitment
        // reconciliation step catches this.
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        jdbcTemplate.update("UPDATE audit_record SET payload = ? WHERE id = ?",
                "{\"accountNumber\":\"99999\"}", created.id());

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isFalse();
        assertThat(verify.firstViolation().detail()).contains("accountNumber");
    }

    @Test
    void verifyDetectsAForgedTombstoneOnAnAlreadyRedactedField() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        post("/audit/events/" + created.id() + "/redact", DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "test"), AuditEventResponse.class);

        // Forge the tombstone to embed a different (fake) commitment, simulating an attacker
        // trying to disguise a different original value as a legitimate redaction.
        jdbcTemplate.update("UPDATE audit_record SET payload = ? WHERE id = ?",
                "{\"accountNumber\":\"[REDACTED:sha256:" + "0".repeat(64) + "]\"}", created.id());

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isFalse();
    }

    @Test
    void redactingAnUnknownFieldIsRejected() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<ErrorResponse> response = post("/audit/events/" + created.id() + "/redact",
                DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("doesNotExist"), "reason", "test"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void redactingAnotherTenantsRecordReturns404() {
        AuditEventResponse created = createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-2",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ResponseEntity<ErrorResponse> response = post("/audit/events/" + created.id() + "/redact",
                DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "test"), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void redactingAnAlreadyRedactedFieldIsIdempotent() {
        AuditEventResponse created = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1",
                Map.of("accountNumber", "12345"), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        post("/audit/events/" + created.id() + "/redact", DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "first"), AuditEventResponse.class);
        ResponseEntity<AuditEventResponse> second = post("/audit/events/" + created.id() + "/redact",
                DEFAULT_TENANT, new String[]{Roles.USER},
                Map.of("fields", List.of("accountNumber"), "reason", "second"), AuditEventResponse.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().redactedFields()).containsExactly("accountNumber");
    }
}
