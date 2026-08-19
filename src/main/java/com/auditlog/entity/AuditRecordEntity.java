package com.auditlog.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only by design: no setters exist. Once persisted, a record is never mutated by
 * application code -- the append-only guarantee (docs/DECISIONS.md ADR-010) is enforced at
 * both the API layer (no PUT/PATCH/DELETE mapping exists) and here at the entity layer.
 */
@Entity
@Table(name = "audit_record")
public class AuditRecordEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sequence_no", nullable = false, updatable = false, unique = true)
    private long sequenceNo;

    @Column(name = "event_type", nullable = false, updatable = false, length = 200)
    private String eventType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 200)
    private String actorId;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 200)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 200)
    private String resourceId;

    @Convert(converter = JsonNodeConverter.class)
    @Lob
    @Column(name = "payload", nullable = false, updatable = false)
    private JsonNode payload;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "record_hash", nullable = false, updatable = false, length = 64)
    private String recordHash;

    @Column(name = "previous_hash", nullable = false, updatable = false, length = 64)
    private String previousHash;

    protected AuditRecordEntity() {
        // JPA
    }

    public AuditRecordEntity(UUID id, long sequenceNo, String eventType, String actorId,
                              String resourceType, String resourceId, JsonNode payload,
                              OffsetDateTime eventTimestamp, OffsetDateTime recordedAt,
                              String recordHash, String previousHash) {
        this.id = id;
        this.sequenceNo = sequenceNo;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.eventTimestamp = eventTimestamp;
        this.recordedAt = recordedAt;
        this.recordHash = recordHash;
        this.previousHash = previousHash;
    }

    public UUID getId() {
        return id;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public OffsetDateTime getEventTimestamp() {
        return eventTimestamp;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }

    public String getRecordHash() {
        return recordHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }
}
