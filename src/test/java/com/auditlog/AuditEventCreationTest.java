package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.dto.ErrorResponse;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventCreationTest extends AbstractApiIntegrationTest {

    @Test
    void createsEventSuccessfully() {
        Map<String, Object> request = Map.of(
                "eventType", "USER_LOGIN",
                "actorId", "user-1",
                "resourceType", "ACCOUNT",
                "resourceId", "acct-1",
                "payload", Map.of("ip", "127.0.0.1"),
                "timestamp", "2026-01-01T00:00:00Z");

        ResponseEntity<AuditEventResponse> response = post("/audit/events", DEFAULT_TENANT,
                new String[]{Roles.USER}, request, AuditEventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().sequenceNo()).isEqualTo(1L);
        assertThat(response.getBody().tenantId()).isEqualTo(DEFAULT_TENANT);
        assertThat(response.getBody().recordHash()).hasSize(64);
        assertThat(response.getBody().recordedAt()).isNotNull();
    }

    @Test
    void rejectsRequestMissingRequiredFields() {
        Map<String, Object> request = Map.of("actorId", "user-1");

        ResponseEntity<ErrorResponse> response = post("/audit/events", DEFAULT_TENANT,
                new String[]{Roles.USER}, request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
