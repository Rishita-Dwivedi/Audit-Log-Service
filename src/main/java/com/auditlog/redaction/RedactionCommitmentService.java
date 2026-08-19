package com.auditlog.redaction;

import com.auditlog.entity.AuditRecordEntity;
import com.auditlog.hash.PayloadCanonicalizer;
import com.auditlog.hash.Sha256;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Implements the field-commitment scheme (docs/DECISIONS.md ADR-003): at write time, every
 * top-level payload field gets a salted commitment (SHA-256(salt|fieldName|canonicalValue)).
 * HashChainService hashes the commitments, never the raw values, which is what allows a field
 * to be redacted later without invalidating record_hash. Scope limitation, stated plainly: only
 * top-level payload fields are covered -- nested-object/array field redaction is out of scope
 * for this milestone.
 */
@Service
public class RedactionCommitmentService {

    private static final String TOMBSTONE_PREFIX = "[REDACTED:sha256:";
    private static final String TOMBSTONE_SUFFIX = "]";

    private final PayloadCanonicalizer payloadCanonicalizer;
    private final ObjectMapper objectMapper;

    public RedactionCommitmentService(PayloadCanonicalizer payloadCanonicalizer, ObjectMapper objectMapper) {
        this.payloadCanonicalizer = payloadCanonicalizer;
        this.objectMapper = objectMapper;
    }

    public String generateSalt() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Computes a commitment for every top-level field in payload. Empty payload -> empty object. */
    public JsonNode computeCommitments(JsonNode payload, String salt) {
        ObjectNode result = objectMapper.createObjectNode();
        if (payload != null && payload.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = payload.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                result.put(entry.getKey(), computeFieldCommitment(entry.getKey(), entry.getValue(), salt));
            }
        }
        return result;
    }

    public String computeFieldCommitment(String fieldName, JsonNode value, String salt) {
        String canonicalValue = payloadCanonicalizer.canonicalize(value);
        return Sha256.hex(salt + "|" + fieldName + "|" + canonicalValue);
    }

    public String tombstone(String commitment) {
        return TOMBSTONE_PREFIX + commitment + TOMBSTONE_SUFFIX;
    }

    public Optional<String> extractCommitmentFromTombstone(String value) {
        if (value == null || !value.startsWith(TOMBSTONE_PREFIX) || !value.endsWith(TOMBSTONE_SUFFIX)) {
            return Optional.empty();
        }
        return Optional.of(value.substring(TOMBSTONE_PREFIX.length(), value.length() - TOMBSTONE_SUFFIX.length()));
    }

    /**
     * Reconciles a record's current payload against its stored, immutable field_commitments.
     * For a non-redacted field, recomputes the commitment from the current raw value and
     * compares it to what was stored -- a mismatch means the raw payload was tampered without
     * updating the commitment (record_hash alone would NOT catch this, since record_hash is
     * computed from the commitments, not the raw payload). For a redacted field, confirms the
     * tombstone's embedded commitment matches what was stored, rather than trying to recompute
     * from a value that no longer exists. Returns human-readable problem descriptions, empty if
     * everything reconciles.
     */
    public List<String> verifyFieldCommitments(AuditRecordEntity record) {
        List<String> problems = new ArrayList<>();
        JsonNode payload = record.getPayload();
        JsonNode commitments = record.getFieldCommitments();
        Set<String> redactedNames = record.redactedFieldNames();

        Set<String> payloadFields = new TreeSet<>();
        payload.fieldNames().forEachRemaining(payloadFields::add);
        Set<String> commitmentFields = new TreeSet<>();
        commitments.fieldNames().forEachRemaining(commitmentFields::add);

        if (!payloadFields.equals(commitmentFields)) {
            problems.add("Payload field set does not match stored field_commitments "
                    + "(a field was added or removed outside the write/redact path)");
            return problems;
        }

        for (String field : payloadFields) {
            String storedCommitment = commitments.get(field).asText();
            if (redactedNames.contains(field)) {
                Optional<String> embedded = extractCommitmentFromTombstone(payload.get(field).asText(null));
                if (embedded.isEmpty() || !embedded.get().equals(storedCommitment)) {
                    problems.add("Redacted field '" + field + "' tombstone does not match its stored commitment");
                }
            } else {
                String recomputed = computeFieldCommitment(field, payload.get(field), record.getSalt());
                if (!recomputed.equals(storedCommitment)) {
                    problems.add("Field '" + field + "' content does not match its stored commitment "
                            + "(payload tampered without updating commitment)");
                }
            }
        }
        return problems;
    }
}
