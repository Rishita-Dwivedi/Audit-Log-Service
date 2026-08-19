package com.auditlog.entity;

import com.auditlog.domain.AuditRecordStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Append-only, with exactly one deliberate, narrowly-scoped exception: applyRedaction()
 * (docs/DECISIONS.md ADR-003, Milestone 8). Every other field remains genuinely immutable
 * (docs/DECISIONS.md ADR-010) -- redaction never touches sequence_no, record_hash,
 * previous_hash, tenant_id, or field_commitments; it only replaces specific payload values
 * with tombstones and updates status/redacted_fields/redacted_at/redacted_by.
 */
@Entity
@Table(name = "audit_record")
public class AuditRecordEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sequence_no", nullable = false, updatable = false, unique = true)
    private long sequenceNo;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 200)
    private String tenantId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 200)
    private String eventType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 200)
    private String actorId;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 200)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 200)
    private String resourceId;

    /** Mutable ONLY via applyRedaction() -- see class Javadoc. */
    @Convert(converter = JsonNodeConverter.class)
    @Lob
    @Column(name = "payload", nullable = false)
    private JsonNode payload;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "record_hash", nullable = false, updatable = false, length = 64)
    private String recordHash;

    @Column(name = "previous_hash", nullable = false, updatable = false, length = 64)
    private String previousHash;

    /** Per-record random salt for the field-commitment scheme. Never changes. */
    @Column(name = "salt", nullable = false, updatable = false, length = 64)
    private String salt;

    /** fieldName -> commitment hex, for every top-level payload field. Never changes, including through redaction -- this is what makes redaction not invalidate record_hash. */
    @Convert(converter = JsonNodeConverter.class)
    @Lob
    @Column(name = "field_commitments", nullable = false, updatable = false)
    private JsonNode fieldCommitments;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditRecordStatus status;

    /** JSON array of redacted field names. Mutable ONLY via applyRedaction(). */
    @Convert(converter = JsonNodeConverter.class)
    @Lob
    @Column(name = "redacted_fields", nullable = false)
    private JsonNode redactedFields;

    @Column(name = "redacted_at")
    private OffsetDateTime redactedAt;

    @Column(name = "redacted_by", length = 200)
    private String redactedBy;

    protected AuditRecordEntity() {
        // JPA
    }

    public AuditRecordEntity(UUID id, long sequenceNo, String tenantId, String eventType, String actorId,
                              String resourceType, String resourceId, JsonNode payload,
                              OffsetDateTime eventTimestamp, OffsetDateTime recordedAt,
                              String recordHash, String previousHash, String salt, JsonNode fieldCommitments) {
        this.id = id;
        this.sequenceNo = sequenceNo;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.eventTimestamp = eventTimestamp;
        this.recordedAt = recordedAt;
        this.recordHash = recordHash;
        this.previousHash = previousHash;
        this.salt = salt;
        this.fieldCommitments = fieldCommitments;
        this.status = AuditRecordStatus.ACTIVE;
        this.redactedFields = JsonNodeFactory.instance.arrayNode();
    }

    /**
     * The one deliberate exception to immutability. fieldTombstones maps field name -> the
     * pre-computed tombstone string to write in place of the raw value (built by
     * RedactionCommitmentService, not here -- this method is deliberately free of hashing
     * logic). record_hash, previous_hash, sequence_no, and field_commitments are never touched.
     * Fields already redacted are silently skipped (idempotent). Returns the field names newly
     * redacted by this call (empty if everything requested was already redacted).
     */
    public Set<String> applyRedaction(Map<String, String> fieldTombstones, String redactedBy, OffsetDateTime redactedAt) {
        Set<String> already = redactedFieldNames();
        Set<String> newlyRedacted = new LinkedHashSet<>();
        ObjectNode newPayload = payload.deepCopy();

        for (Map.Entry<String, String> entry : fieldTombstones.entrySet()) {
            if (already.contains(entry.getKey())) {
                continue;
            }
            newPayload.put(entry.getKey(), entry.getValue());
            newlyRedacted.add(entry.getKey());
        }

        if (newlyRedacted.isEmpty()) {
            return newlyRedacted;
        }

        Set<String> allRedacted = new LinkedHashSet<>(already);
        allRedacted.addAll(newlyRedacted);
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        allRedacted.forEach(array::add);

        this.payload = newPayload;
        this.redactedFields = array;
        this.status = AuditRecordStatus.REDACTED;
        this.redactedAt = redactedAt;
        this.redactedBy = redactedBy;
        return newlyRedacted;
    }

    public Set<String> redactedFieldNames() {
        Set<String> names = new LinkedHashSet<>();
        if (redactedFields != null) {
            redactedFields.forEach(node -> names.add(node.asText()));
        }
        return names;
    }

    public UUID getId() {
        return id;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public String getTenantId() {
        return tenantId;
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

    public String getSalt() {
        return salt;
    }

    public JsonNode getFieldCommitments() {
        return fieldCommitments;
    }

    public AuditRecordStatus getStatus() {
        return status;
    }

    public JsonNode getRedactedFields() {
        return redactedFields;
    }

    public OffsetDateTime getRedactedAt() {
        return redactedAt;
    }

    public String getRedactedBy() {
        return redactedBy;
    }
}
