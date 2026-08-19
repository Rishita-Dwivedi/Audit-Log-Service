package com.auditlog.hash;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadCanonicalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayloadCanonicalizer canonicalizer = new PayloadCanonicalizer(objectMapper);

    @Test
    void keyOrderDoesNotAffectCanonicalOutput() throws Exception {
        JsonNode a = objectMapper.readTree("{\"b\":1,\"a\":2}");
        JsonNode b = objectMapper.readTree("{\"a\":2,\"b\":1}");

        assertThat(canonicalizer.canonicalize(a)).isEqualTo(canonicalizer.canonicalize(b));
    }

    @Test
    void nestedObjectKeysAreSortedRecursively() throws Exception {
        JsonNode node = objectMapper.readTree("{\"outer\":{\"z\":1,\"a\":2}}");

        assertThat(canonicalizer.canonicalize(node)).isEqualTo("{\"outer\":{\"a\":2,\"z\":1}}");
    }

    @Test
    void arrayOrderIsPreserved() throws Exception {
        JsonNode a = objectMapper.readTree("{\"list\":[3,1,2]}");
        JsonNode b = objectMapper.readTree("{\"list\":[1,2,3]}");

        assertThat(canonicalizer.canonicalize(a)).isNotEqualTo(canonicalizer.canonicalize(b));
    }

    @Test
    void emptyPayloadCanonicalizesToEmptyObject() throws Exception {
        JsonNode node = objectMapper.readTree("{}");

        assertThat(canonicalizer.canonicalize(node)).isEqualTo("{}");
    }
}
