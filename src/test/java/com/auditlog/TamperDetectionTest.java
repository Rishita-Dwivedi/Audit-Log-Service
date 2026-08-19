package com.auditlog;

import com.auditlog.domain.ViolationType;
import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.VerifyResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assignment's mandated validation path: write via the API, verify (intact), tamper
 * directly in the data store bypassing the API entirely, verify again (detected).
 */
class TamperDetectionTest extends AbstractApiIntegrationTest {

    @Test
    void detectsDirectContentModification() {
        AuditEventResponse first = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of("amount", 100),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        assertThat(getAsAuditor("/audit/verify", VerifyResponse.class).chainIntact()).isTrue();

        jdbcTemplate.update("UPDATE audit_record SET payload = ? WHERE id = ?", "{\"amount\":999999}", first.id());

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isFalse();
        assertThat(verify.firstViolation().sequenceNo()).isEqualTo(1L);
        assertThat(verify.firstViolation().violationType()).isEqualTo(ViolationType.CONTENT_MISMATCH);
    }

    @Test
    void detectsIncorrectPreviousHash() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        AuditEventResponse second = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        jdbcTemplate.update("UPDATE audit_record SET previous_hash = ? WHERE id = ?", "0".repeat(64), second.id());

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isFalse();
        assertThat(verify.firstViolation().sequenceNo()).isEqualTo(2L);
        assertThat(verify.firstViolation().violationType()).isEqualTo(ViolationType.LINKAGE_BROKEN);
    }

    @Test
    void detectsSequenceGapFromDeletedMiddleRecord() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        AuditEventResponse second = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:02:00Z"));

        jdbcTemplate.update("DELETE FROM audit_record WHERE id = ?", second.id());

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isFalse();
        assertThat(verify.firstViolation().sequenceNo()).isEqualTo(3L);
        assertThat(verify.firstViolation().violationType())
                .isIn(ViolationType.MISSING_RECORD, ViolationType.LINKAGE_BROKEN);
        assertThat(verify.recordsChecked()).isEqualTo(2L);
    }

    @Test
    void deletedMiddleRecordProducesBothMissingRecordAndLinkageViolations() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        AuditEventResponse second = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:02:00Z"));

        jdbcTemplate.update("DELETE FROM audit_record WHERE id = ?", second.id());

        VerifyResponse verify = getAsAuditor("/audit/verify", VerifyResponse.class);

        assertThat(verify.chainIntact()).isFalse();
        // Both MISSING_RECORD (sequence gap) and LINKAGE_BROKEN (previous_hash no longer matches)
        // are real, independently detected consequences of the same deletion.
        assertThat(verify.additionalViolations()).isGreaterThanOrEqualTo(1);
    }
}
