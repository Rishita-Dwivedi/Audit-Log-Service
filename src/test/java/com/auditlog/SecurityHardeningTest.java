package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/EVALUATION_CLOSURE_MATRIX.md items 4 (SEC-06 idempotency), 5 (SEC-06 request limits),
 * 6 (SEC-06 CORS), 10 (SEC-09 operational monitoring).
 */
class SecurityHardeningTest extends AbstractApiIntegrationTest {

    @Test
    void duplicateIdempotencyKeyReturnsTheSameRecordInsteadOfCreatingANewOne() {
        Map<String, Object> request = Map.of(
                "eventType", "USER_LOGIN", "actorId", "user-1", "resourceType", "ACCOUNT",
                "resourceId", "acct-1", "payload", Map.of(), "timestamp", "2026-01-01T00:00:00Z");

        ResponseEntity<AuditEventResponse> first = postWithIdempotencyKey(
                "/audit/events", DEFAULT_TENANT, new String[]{Roles.USER}, request, "key-1", AuditEventResponse.class);
        ResponseEntity<AuditEventResponse> second = postWithIdempotencyKey(
                "/audit/events", DEFAULT_TENANT, new String[]{Roles.USER}, request, "key-1", AuditEventResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(second.getBody().sequenceNo()).isEqualTo(first.getBody().sequenceNo());
    }

    @Test
    void requestsWithoutAnIdempotencyKeyAreUnaffected() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        AuditEventResponse second = createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        assertThat(second.sequenceNo()).isEqualTo(2L);
    }

    @Test
    void oversizedRequestBodyIsRejected() {
        // Sets Content-Length explicitly via a raw request rather than relying on the test
        // HTTP client's own buffering behavior (which may use chunked transfer-encoding for a
        // large body without declaring Content-Length up front -- exactly the documented gap
        // in RequestSizeLimitFilter's Javadoc: this check is declared-length based, not a
        // streaming byte-count limit).
        HttpHeaders headers = authHeaders(DEFAULT_TENANT, Roles.USER);
        byte[] oversizedBody = new byte[2_000_000];
        headers.setContentLength(oversizedBody.length);

        ResponseEntity<String> response = restTemplate.exchange(baseUrl("/audit/events"), HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(oversizedBody, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void noCorsHeaderIsReturnedForACrossOriginRequest() {
        HttpHeaders headers = authHeaders(DEFAULT_TENANT, Roles.USER);
        headers.set("Origin", "https://not-this-service.example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl("/audit/events"), HttpMethod.GET, new org.springframework.http.HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void healthEndpointIsPubliclyReachableWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }
}
