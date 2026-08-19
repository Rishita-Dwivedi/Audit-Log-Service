package com.auditlog.hash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HashChainServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HashChainService hashChainService = new HashChainService(new PayloadCanonicalizer(objectMapper));

    @Test
    void genesisHashIsDeterministicAndSha256Sized() {
        assertThat(hashChainService.genesisHash()).isEqualTo(hashChainService.genesisHash());
        assertThat(hashChainService.genesisHash()).hasSize(64);
    }

    @Test
    void computeRecordHashIsDeterministicForSameInput() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"amount\":100}");
        OffsetDateTime ts = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        String hash1 = hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payload, ts, 1L, "prev");
        String hash2 = hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payload, ts, 1L, "prev");

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void changingAnyFieldChangesTheHash() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"amount\":100}");
        OffsetDateTime ts = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        String baseline = hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payload, ts, 1L, "prev");

        assertThat(hashChainService.computeRecordHash("tenant-2", "EVT", "actor", "TYPE", "id-1", payload, ts, 1L, "prev"))
                .isNotEqualTo(baseline);
        assertThat(hashChainService.computeRecordHash("tenant-1", "OTHER", "actor", "TYPE", "id-1", payload, ts, 1L, "prev"))
                .isNotEqualTo(baseline);
        assertThat(hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payload, ts, 2L, "prev"))
                .isNotEqualTo(baseline);
        assertThat(hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payload, ts, 1L, "other-prev"))
                .isNotEqualTo(baseline);
    }

    @Test
    void hashIsUnaffectedByPayloadKeyReordering() throws Exception {
        JsonNode payloadA = objectMapper.readTree("{\"b\":1,\"a\":2}");
        JsonNode payloadB = objectMapper.readTree("{\"a\":2,\"b\":1}");
        OffsetDateTime ts = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        String hashA = hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payloadA, ts, 1L, "prev");
        String hashB = hashChainService.computeRecordHash("tenant-1", "EVT", "actor", "TYPE", "id-1", payloadB, ts, 1L, "prev");

        assertThat(hashA).isEqualTo(hashB);
    }
}
