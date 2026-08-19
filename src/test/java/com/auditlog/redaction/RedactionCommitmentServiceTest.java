package com.auditlog.redaction;

import com.auditlog.domain.AuditRecordStatus;
import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.hash.PayloadCanonicalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionCommitmentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedactionCommitmentService service =
            new RedactionCommitmentService(new PayloadCanonicalizer(objectMapper), objectMapper);

    @Test
    void computeCommitmentsCoversEveryTopLevelField() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"accountNumber\":\"12345\",\"amount\":100}");

        JsonNode commitments = service.computeCommitments(payload, "salt-1");

        assertThat(commitments.has("accountNumber")).isTrue();
        assertThat(commitments.has("amount")).isTrue();
        assertThat(commitments.get("accountNumber").asText()).hasSize(64);
    }

    @Test
    void commitmentDependsOnFieldNameNotJustValue() throws Exception {
        // Two different fields with the same value must not produce the same commitment --
        // otherwise an observer could tell two fields hold equal values just from commitments.
        JsonNode payload = objectMapper.readTree("{\"a\":\"same\",\"b\":\"same\"}");

        JsonNode commitments = service.computeCommitments(payload, "salt-1");

        assertThat(commitments.get("a").asText()).isNotEqualTo(commitments.get("b").asText());
    }

    @Test
    void differentSaltProducesDifferentCommitment() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"accountNumber\":\"12345\"}");

        JsonNode c1 = service.computeCommitments(payload, "salt-1");
        JsonNode c2 = service.computeCommitments(payload, "salt-2");

        assertThat(c1.get("accountNumber").asText()).isNotEqualTo(c2.get("accountNumber").asText());
    }

    @Test
    void tombstoneRoundTripsToTheSameCommitment() {
        String commitment = "abc123";

        String tombstone = service.tombstone(commitment);
        Optional<String> extracted = service.extractCommitmentFromTombstone(tombstone);

        assertThat(tombstone).isEqualTo("[REDACTED:sha256:abc123]");
        assertThat(extracted).contains(commitment);
    }

    @Test
    void extractCommitmentFromNonTombstoneValueIsEmpty() {
        assertThat(service.extractCommitmentFromTombstone("plain value")).isEmpty();
    }

    @Test
    void verifyFieldCommitmentsPassesForAnUntamperedActiveRecord() throws Exception {
        AuditRecordEntity record = buildRecord("{\"accountNumber\":\"12345\",\"amount\":100}", "salt-1");

        List<String> problems = service.verifyFieldCommitments(record);

        assertThat(problems).isEmpty();
    }

    @Test
    void verifyFieldCommitmentsCatchesTamperedRawValueEvenThoughRecordHashDoesNotCoverIt() throws Exception {
        // This is the gap record_hash alone can't catch: record_hash is computed from
        // field_commitments, not the raw payload, so tampering a raw value directly without
        // updating its commitment leaves record_hash unchanged. verifyFieldCommitments exists
        // specifically to catch this.
        AuditRecordEntity record = buildRecord("{\"accountNumber\":\"12345\"}", "salt-1");
        AuditRecordEntity tampered = withTamperedPayload(record, "{\"accountNumber\":\"99999\"}");

        List<String> problems = service.verifyFieldCommitments(tampered);

        assertThat(problems).isNotEmpty();
        assertThat(problems.get(0)).contains("accountNumber");
    }

    @Test
    void verifyFieldCommitmentsPassesForALegitimatelyRedactedField() throws Exception {
        AuditRecordEntity record = buildRecord("{\"accountNumber\":\"12345\"}", "salt-1");
        String commitment = record.getFieldCommitments().get("accountNumber").asText();
        Map<String, String> tombstones = Map.of("accountNumber", service.tombstone(commitment));
        record.applyRedaction(tombstones, "tester", OffsetDateTime.now());

        List<String> problems = service.verifyFieldCommitments(record);

        assertThat(problems).isEmpty();
    }

    @Test
    void verifyFieldCommitmentsCatchesAForgedTombstone() throws Exception {
        AuditRecordEntity record = buildRecord("{\"accountNumber\":\"12345\"}", "salt-1");
        // A forged tombstone embedding a commitment that doesn't match what's on record --
        // simulating an attacker trying to fake a redaction to hide a different original value.
        Map<String, String> forgedTombstones = Map.of("accountNumber", service.tombstone("0".repeat(64)));
        record.applyRedaction(forgedTombstones, "attacker", OffsetDateTime.now());

        List<String> problems = service.verifyFieldCommitments(record);

        assertThat(problems).isNotEmpty();
    }

    private AuditRecordEntity buildRecord(String payloadJson, String salt) throws Exception {
        JsonNode payload = objectMapper.readTree(payloadJson);
        JsonNode commitments = service.computeCommitments(payload, salt);
        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        return new AuditRecordEntity(UUID.randomUUID(), 1L, "tenant-1", "TEST_EVENT", "actor-1",
                "TYPE", "id-1", payload, now, now, "recordhash", "prevhash", salt, commitments);
    }

    private AuditRecordEntity withTamperedPayload(AuditRecordEntity original, String newPayloadJson) throws Exception {
        // Simulates a direct data-store tamper: a new entity instance with the same
        // commitments/salt as the original but a different raw payload.
        return new AuditRecordEntity(original.getId(), original.getSequenceNo(), original.getTenantId(),
                original.getEventType(), original.getActorId(), original.getResourceType(), original.getResourceId(),
                objectMapper.readTree(newPayloadJson), original.getEventTimestamp(), original.getRecordedAt(),
                original.getRecordHash(), original.getPreviousHash(), original.getSalt(), original.getFieldCommitments());
    }
}
