package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import com.auditlog.security.JwtService;
import com.auditlog.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Full black-box HTTP tests against a real (in-memory H2) database -- matches the assignment's
 * framing that the system is validated entirely through its own APIs. JdbcTemplate is used
 * only to simulate direct data-store tampering, bypassing the application, per the assignment's
 * explicit validation step.
 *
 * All requests need a valid Bearer JWT now that authentication is enforced
 * (docs/EVALUATION_CLOSURE_MATRIX.md item 3, SEC-03) -- tokens are minted directly via
 * JwtService rather than through the /dev/auth/token HTTP endpoint, since that endpoint exists
 * for manual/live demoing, not to slow down the test suite with an extra round trip per test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractApiIntegrationTest {

    protected static final String DEFAULT_TENANT = "tenant-1";
    protected static final String OTHER_TENANT = "tenant-2";

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JwtService jwtService;

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void resetChain() {
        jdbcTemplate.update("DELETE FROM audit_record");
        jdbcTemplate.update("UPDATE chain_head SET last_sequence_no = 0, last_record_hash = NULL WHERE id = 1");
    }

    protected HttpHeaders authHeaders(String tenantId, String... roles) {
        Set<String> roleSet = roles.length == 0 ? Set.of(Roles.USER) : Set.of(roles);
        String token = jwtService.issueToken("test-subject", tenantId, roleSet, Duration.ofMinutes(5));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected AuditEventResponse createEvent(String eventType, String actorId, String resourceType, String resourceId,
                                              Map<String, Object> payload, OffsetDateTime timestamp) {
        return createEvent(DEFAULT_TENANT, eventType, actorId, resourceType, resourceId, payload, timestamp);
    }

    protected AuditEventResponse createEvent(String tenantId, String eventType, String actorId, String resourceType,
                                              String resourceId, Map<String, Object> payload, OffsetDateTime timestamp) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventType", eventType);
        request.put("actorId", actorId);
        request.put("resourceType", resourceType);
        request.put("resourceId", resourceId);
        request.put("payload", payload);
        request.put("timestamp", timestamp.toString());
        return post("/audit/events", tenantId, new String[]{Roles.USER}, request, AuditEventResponse.class).getBody();
    }

    protected <T> ResponseEntity<T> post(String path, String tenantId, String[] roles, Object body, Class<T> responseType) {
        return restTemplate.exchange(baseUrl(path), HttpMethod.POST, new HttpEntity<>(body, authHeaders(tenantId, roles)), responseType);
    }

    protected <T> ResponseEntity<T> get(String path, String tenantId, String[] roles, Class<T> responseType) {
        return restTemplate.exchange(baseUrl(path), HttpMethod.GET, new HttpEntity<>(authHeaders(tenantId, roles)), responseType);
    }

    protected <T> T get(String path, Class<T> responseType) {
        return get(path, DEFAULT_TENANT, new String[]{Roles.USER}, responseType).getBody();
    }

    protected <T> T getAsAuditor(String path, Class<T> responseType) {
        return get(path, DEFAULT_TENANT, new String[]{Roles.AUDITOR}, responseType).getBody();
    }
}
