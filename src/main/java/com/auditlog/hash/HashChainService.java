package com.auditlog.hash;

import com.auditlog.entity.AuditRecordEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * The single place in the codebase that computes or verifies a record hash. Every write and
 * every verification pass routes through this class -- nothing else derives a hash
 * independently (docs/ARCHITECTURE.md, "Architecture principle").
 *
 * IMPORTANT (Milestone 8, docs/DECISIONS.md ADR-003): the hash is computed over each field's
 * per-field commitment, NOT the raw payload value. This is what lets a field be redacted later
 * (its raw value replaced with a tombstone) without invalidating record_hash -- the hash never
 * depended on the raw value directly, only on a commitment that survives redaction unchanged.
 * See com.auditlog.redaction.RedactionCommitmentService for how commitments are computed.
 */
@Service
public class HashChainService {

    /** Never null, never all-zero (both would be ambiguous with "not yet computed"). */
    public static final String GENESIS_SEED = "AUDIT-CHAIN-GENESIS-v1";

    private final PayloadCanonicalizer payloadCanonicalizer;

    public HashChainService(PayloadCanonicalizer payloadCanonicalizer) {
        this.payloadCanonicalizer = payloadCanonicalizer;
    }

    public String genesisHash() {
        return Sha256.hex(GENESIS_SEED);
    }

    public String computeRecordHash(String tenantId, String eventType, String actorId, String resourceType,
                                     String resourceId, JsonNode fieldCommitments, OffsetDateTime eventTimestamp,
                                     long sequenceNo, String previousHash) {
        String canonicalCommitments = payloadCanonicalizer.canonicalize(fieldCommitments);
        String canonicalString = String.join("|",
                nullToEmpty(tenantId),
                nullToEmpty(eventType),
                nullToEmpty(actorId),
                nullToEmpty(resourceType),
                nullToEmpty(resourceId),
                canonicalCommitments,
                eventTimestamp.toInstant().toString(),
                Long.toString(sequenceNo),
                nullToEmpty(previousHash));
        return Sha256.hex(canonicalString);
    }

    public String computeRecordHash(AuditRecordEntity record) {
        return computeRecordHash(record.getTenantId(), record.getEventType(), record.getActorId(),
                record.getResourceType(), record.getResourceId(), record.getFieldCommitments(),
                record.getEventTimestamp(), record.getSequenceNo(), record.getPreviousHash());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
