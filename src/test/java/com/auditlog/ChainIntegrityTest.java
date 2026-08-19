package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.VerifyResponse;
import com.auditlog.hash.HashChainService;
import com.auditlog.hash.PayloadCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChainIntegrityTest extends AbstractApiIntegrationTest {

    @Test
    void firstRecordUsesGenesisValue() {
        AuditEventResponse first = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        String genesisHash = new HashChainService(new PayloadCanonicalizer(new ObjectMapper())).genesisHash();

        assertThat(first.sequenceNo()).isEqualTo(1L);
        assertThat(first.previousHash()).isEqualTo(genesisHash);
    }

    @Test
    void secondRecordPointsToFirstRecordsHash() {
        AuditEventResponse first = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        AuditEventResponse second = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        assertThat(second.sequenceNo()).isEqualTo(2L);
        assertThat(second.previousHash()).isEqualTo(first.recordHash());
    }

    @Test
    void verificationSucceedsForAnUntamperedChain() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:02:00Z"));

        VerifyResponse verify = restTemplate.getForObject(baseUrl("/audit/verify"), VerifyResponse.class);

        assertThat(verify.chainIntact()).isTrue();
        assertThat(verify.recordsChecked()).isEqualTo(3L);
        assertThat(verify.firstViolation()).isNull();
    }
}
