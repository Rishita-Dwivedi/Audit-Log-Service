package com.auditlog;

import com.auditlog.dto.AuditEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Full black-box HTTP tests against a real (in-memory H2) database -- matches the assignment's
 * framing that the system is validated entirely through its own APIs. JdbcTemplate is used
 * only to simulate direct data-store tampering, bypassing the application, per the assignment's
 * explicit validation step.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractApiIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void resetChain() {
        jdbcTemplate.update("DELETE FROM audit_record");
        jdbcTemplate.update("UPDATE chain_head SET last_sequence_no = 0, last_record_hash = NULL WHERE id = 1");
    }

    protected AuditEventResponse createEvent(String eventType, String actorId, String resourceType, String resourceId,
                                              Map<String, Object> payload, OffsetDateTime timestamp) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventType", eventType);
        request.put("actorId", actorId);
        request.put("resourceType", resourceType);
        request.put("resourceId", resourceId);
        request.put("payload", payload);
        request.put("timestamp", timestamp.toString());
        return restTemplate.postForEntity(baseUrl("/audit/events"), request, AuditEventResponse.class).getBody();
    }
}
