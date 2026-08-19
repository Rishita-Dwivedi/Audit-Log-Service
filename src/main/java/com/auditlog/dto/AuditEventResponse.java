package com.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

/** The API contract for a record -- deliberately separate from AuditRecordEntity. */
public record AuditEventResponse(
        UUID id,
        long sequenceNo,
        String tenantId,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        JsonNode payload,
        OffsetDateTime eventTimestamp,
        OffsetDateTime recordedAt,
        String recordHash,
        String previousHash
) {
}
