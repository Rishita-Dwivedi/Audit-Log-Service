package com.auditlog;

import com.auditlog.dto.AuditEventPageResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventQueryTest extends AbstractApiIntegrationTest {

    @Test
    void filtersByActorId() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-2", "ACCOUNT", "acct-2", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        AuditEventPageResponse response = restTemplate.getForObject(
                baseUrl("/audit/events?actorId=user-1"), AuditEventPageResponse.class);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).actorId()).isEqualTo("user-1");
    }

    @Test
    void filtersByResourceTypeAndResourceId() {
        createEvent("RECORD_UPDATED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("RECORD_UPDATED", "user-1", "ACCOUNT", "acct-2", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        AuditEventPageResponse response = restTemplate.getForObject(
                baseUrl("/audit/events?resourceType=ACCOUNT&resourceId=acct-2"), AuditEventPageResponse.class);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).resourceId()).isEqualTo("acct-2");
    }

    @Test
    void filtersByEventType() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("PERMISSION_GRANTED", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:01:00Z"));

        AuditEventPageResponse response = restTemplate.getForObject(
                baseUrl("/audit/events?eventType=PERMISSION_GRANTED"), AuditEventPageResponse.class);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).eventType()).isEqualTo("PERMISSION_GRANTED");
    }

    @Test
    void filtersByTimeRange() {
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"));

        AuditEventPageResponse response = restTemplate.getForObject(
                baseUrl("/audit/events?from=2026-03-01T00:00:00Z&to=2026-12-31T00:00:00Z"), AuditEventPageResponse.class);

        assertThat(response.items()).hasSize(1);
    }

    @Test
    void paginatesUsingCursor() {
        for (int i = 0; i < 5; i++) {
            createEvent("USER_LOGIN", "user-1", "ACCOUNT", "acct-1", Map.of(),
                    OffsetDateTime.parse("2026-01-01T00:00:00Z").plusMinutes(i));
        }

        AuditEventPageResponse firstPage = restTemplate.getForObject(
                baseUrl("/audit/events?pageSize=2"), AuditEventPageResponse.class);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.hasMore()).isTrue();

        AuditEventPageResponse secondPage = restTemplate.getForObject(
                baseUrl("/audit/events?pageSize=2&afterSequenceNo=" + firstPage.nextCursor()), AuditEventPageResponse.class);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.items().get(0).sequenceNo()).isGreaterThan(firstPage.nextCursor());
    }
}
