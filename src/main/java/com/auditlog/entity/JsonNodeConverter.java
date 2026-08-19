package com.auditlog.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the original payload as-received (not the canonical hashing form -- see
 * com.auditlog.hash.PayloadCanonicalizer for that). Canonicalization is applied only
 * when computing a hash, never to what is persisted.
 */
@Converter
class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(dbData);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize audit payload", e);
        }
    }
}
