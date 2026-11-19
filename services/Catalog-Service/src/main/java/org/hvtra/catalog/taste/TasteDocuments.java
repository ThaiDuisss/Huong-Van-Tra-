package org.hvtra.catalog.taste;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
class TasteDocuments {
    static final String VERSION_CODE = "[A-Za-z0-9][A-Za-z0-9._-]{0,39}";
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private final ObjectMapper mapper;

    ObjectNode object() {
        return mapper.createObjectNode();
    }

    static boolean isCode(String value) {
        return value != null && !"UNKNOWN".equals(value) && CODE.matcher(value).matches();
    }

    static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    int size(JsonNode node) {
        return bytes(node).length;
    }

    String hash(JsonNode canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(canonical)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    byte[] bytes(JsonNode value) {
        try {
            return mapper.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsBytes(value);
        } catch (JsonProcessingException failure) {
            throw TasteFailure.invalid("JSON không hợp lệ");
        }
    }

    // The Customer taxonomy adapter uses the same compact JSON, key order and NFC.
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
            ArrayNode ordered = mapper.createArrayNode();
            node.forEach(item -> ordered.add(canonical(item)));
            return ordered;
        }
        if (node.isTextual()) {
            return TextNode.valueOf(Normalizer.normalize(node.textValue(), Normalizer.Form.NFC));
        }
        if (node.isNull() || node.isBoolean() || node.isIntegralNumber()) {
            return node;
        }
        throw TasteFailure.invalid("Số thập phân trong tài liệu phải được biểu diễn bằng chuỗi");
    }
}
