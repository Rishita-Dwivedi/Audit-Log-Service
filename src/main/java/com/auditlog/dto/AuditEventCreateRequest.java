package com.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * timestamp is required and caller-supplied (docs/DECISIONS.md ADR-002): it is stored as
 * event_timestamp and treated as the caller's informational assertion of when the event
 * happened. It is never used for chain ordering -- sequence_no (server-assigned) is the
 * order of truth, and recorded_at (server-assigned, not part of this request) is the
 * independent "when we actually saw this" fact. Requiring it here (rather than defaulting
 * silently when absent) keeps that distinction unambiguous.
 */
public record AuditEventCreateRequest(
        @NotBlank String eventType,
        @NotBlank String actorId,
        @NotBlank String resourceType,
        @NotBlank String resourceId,
        @NotNull JsonNode payload,
        @NotNull OffsetDateTime timestamp
) {
}
