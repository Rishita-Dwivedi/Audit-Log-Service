package com.auditlog.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The API contract for a record -- deliberately separate from AuditRecordEntity.
 * Deliberately does NOT include salt or fieldCommitments (docs/DECISIONS.md ADR-003): exposing
 * either would let a caller brute-force a redacted value offline (recompute
 * SHA256(salt|field|guess) for a low-entropy value space and compare to the leaked commitment),
 * defeating the point of redaction.
 */
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
        String previousHash,
        String status,
        List<String> redactedFields
) {
}
