package com.auditlog.hash;

import com.auditlog.entity.AuditRecordEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

/**
 * The single place in the codebase that computes or verifies a record hash. Every write and
 * every verification pass routes through this class -- nothing else derives a hash
 * independently (docs/ARCHITECTURE.md, "Architecture principle").
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
        return sha256Hex(GENESIS_SEED);
    }

    public String computeRecordHash(String eventType, String actorId, String resourceType, String resourceId,
                                     JsonNode payload, OffsetDateTime eventTimestamp, long sequenceNo,
                                     String previousHash) {
        String canonicalPayload = payloadCanonicalizer.canonicalize(payload);
        String canonicalString = String.join("|",
                nullToEmpty(eventType),
                nullToEmpty(actorId),
                nullToEmpty(resourceType),
                nullToEmpty(resourceId),
                canonicalPayload,
                eventTimestamp.toInstant().toString(),
                Long.toString(sequenceNo),
                nullToEmpty(previousHash));
        return sha256Hex(canonicalString);
    }

    public String computeRecordHash(AuditRecordEntity record) {
        return computeRecordHash(record.getEventType(), record.getActorId(), record.getResourceType(),
                record.getResourceId(), record.getPayload(), record.getEventTimestamp(),
                record.getSequenceNo(), record.getPreviousHash());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
