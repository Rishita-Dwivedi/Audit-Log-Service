package com.auditlog.hash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic serialization of a JSON payload for hashing: object keys are sorted
 * recursively (via TreeMap) so that two payloads which are logically identical but differ
 * only in key order produce the same canonical string. Array order is preserved since order
 * is semantically meaningful there.
 *
 * This is used only as hash input -- see com.auditlog.entity.JsonNodeConverter for how the
 * payload is actually stored (as originally received, not canonicalized).
 *
 * Known limitation (docs/DECISIONS.md, ADR-006): this does not implement a full canonical
 * JSON spec (e.g., RFC 8785) -- number formatting follows Jackson's default long/double
 * serialization rather than a spec-defined representation. Acceptable for this prototype as
 * long as producers are consistent; documented as a scoped trade-off, not silently ignored.
 */
@Component
public class PayloadCanonicalizer {

    private final ObjectMapper objectMapper;

    public PayloadCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalize(JsonNode payload) {
        Object canonical = toCanonicalStructure(payload);
        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to canonicalize payload", e);
        }
    }

    private Object toCanonicalStructure(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), toCanonicalStructure(entry.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(element -> list.add(toCanonicalStructure(element)));
            return list;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.doubleValue();
        }
        return node.asText();
    }
}
