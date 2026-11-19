package org.hvtra.customer.taste;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
class TasteJson {
    private final ObjectMapper mapper;

    ObjectNode object() {
        return mapper.createObjectNode();
    }

    ArrayNode array() {
        return mapper.createArrayNode();
    }

    byte[] bytes(JsonNode node) {
        try {
            return mapper.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsBytes(node);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot encode taste document", failure);
        }
    }

    String sha256(JsonNode node) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(node)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            fields.sort(String::compareTo);
            ObjectNode ordered = object();
            fields.forEach(name -> ordered.set(name, canonical(node.get(name))));
            return ordered;
        }
        if (node.isArray()) {
            ArrayNode ordered = array();
            node.forEach(item -> ordered.add(canonical(item)));
            return ordered;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(Normalizer.normalize(node.textValue(), Normalizer.Form.NFC));
        }
        if (node.isNull() || node.isBoolean() || node.isIntegralNumber()) {
            return node;
        }
        throw new IllegalArgumentException("Unsupported value in canonical taste document");
    }
}
