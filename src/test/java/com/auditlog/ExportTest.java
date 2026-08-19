package com.auditlog;

import com.auditlog.dto.ErrorResponse;
import com.auditlog.dto.ExportBundleResponse;
import com.auditlog.export.ExportBundleService;
import com.auditlog.export.ExportSigningService;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.PublicKey;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/REQUIREMENTS.md FR-B3, docs/DECISIONS.md ADR-013 (signed export manifests, closing
 * docs/EVALUATION_CLOSURE_MATRIX.md item 15, ARC-03).
 */
class ExportTest extends AbstractApiIntegrationTest {

    @Test
    void exportRequiresAtLeastOneFilter() {
        ResponseEntity<ErrorResponse> response = get("/audit/export", DEFAULT_TENANT,
                new String[]{Roles.USER}, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exportReturnsOnlyMatchingRecords() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-2", Map.of(), OffsetDateTime.parse("2026-01-01T00:02:00Z"));

        ExportBundleResponse bundle = get("/audit/export?resourceId=acct-1", ExportBundleResponse.class);

        assertThat(bundle.recordCount()).isEqualTo(2);
        assertThat(bundle.records()).allMatch(r -> r.resourceId().equals("acct-1"));
    }

    @Test
    void chainContextHashOfLastRecordBeforeRangeMatchesFirstExportedRecordsPreviousHash() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-other", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:02:00Z"));

        ExportBundleResponse bundle = get("/audit/export?resourceId=acct-1", ExportBundleResponse.class);

        assertThat(bundle.chainContext().hashOfLastRecordBeforeRange())
                .isEqualTo(bundle.records().get(0).previousHash());
    }

    @Test
    void exportBundleSignatureVerifiesIndependently() throws Exception {
        // Simulates an external recipient with no access to this server: reconstruct the
        // canonical manifest from only the bundle's own published JSON fields, and verify using
        // only the published public key.
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ExportBundleResponse bundle = get("/audit/export?resourceId=acct-1", ExportBundleResponse.class);

        String reconstructedManifest = ExportBundleService.canonicalManifest(
                bundle.exportedAt(), bundle.tenantId(), bundle.recordCount(), bundle.chainContext(), bundle.records());
        PublicKey publicKey = ExportSigningService.decodePublicKey(bundle.signature().publicKeyBase64());
        boolean valid = new ExportSigningService().verify(reconstructedManifest, bundle.signature().signatureBase64(), publicKey);

        assertThat(valid).isTrue();
    }

    @Test
    void tamperedBundleFailsSignatureVerification() throws Exception {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ExportBundleResponse bundle = get("/audit/export?resourceId=acct-1", ExportBundleResponse.class);

        // Simulate an in-transit tamper: swap in a different recordHash for the one exported
        // record, keeping everything else (including the original signature) unchanged.
        List<com.auditlog.dto.AuditEventResponse> tamperedRecords = new ArrayList<>(bundle.records());
        com.auditlog.dto.AuditEventResponse original = tamperedRecords.get(0);
        com.auditlog.dto.AuditEventResponse tampered = new com.auditlog.dto.AuditEventResponse(
                original.id(), original.sequenceNo(), original.tenantId(), original.eventType(), original.actorId(),
                original.resourceType(), original.resourceId(), original.payload(), original.eventTimestamp(),
                original.recordedAt(), "0".repeat(64), original.previousHash(), original.status(), original.redactedFields());
        tamperedRecords.set(0, tampered);

        String tamperedManifest = ExportBundleService.canonicalManifest(
                bundle.exportedAt(), bundle.tenantId(), bundle.recordCount(), bundle.chainContext(), tamperedRecords);
        PublicKey publicKey = ExportSigningService.decodePublicKey(bundle.signature().publicKeyBase64());
        boolean valid = new ExportSigningService().verify(tamperedManifest, bundle.signature().signatureBase64(), publicKey);

        assertThat(valid).isFalse();
    }

    @Test
    void exportIsScopedToCallersOwnTenantUnlessAuditor() {
        createEvent(OTHER_TENANT, "USER_LOGIN", "user-2", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        ExportBundleResponse asNonAuditor = get("/audit/export?resourceId=acct-1&tenantId=" + OTHER_TENANT,
                DEFAULT_TENANT, new String[]{Roles.USER}, ExportBundleResponse.class).getBody();
        assertThat(asNonAuditor.recordCount()).isEqualTo(0);

        ExportBundleResponse asAuditor = get("/audit/export?resourceId=acct-1&tenantId=" + OTHER_TENANT,
                DEFAULT_TENANT, new String[]{Roles.AUDITOR}, ExportBundleResponse.class).getBody();
        assertThat(asAuditor.recordCount()).isEqualTo(1);
    }
}
