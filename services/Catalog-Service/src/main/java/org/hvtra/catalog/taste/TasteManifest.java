package org.hvtra.catalog.taste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
class TasteManifest {
    private static final int MAX_BYTES = 256 * 1024;
    private static final Set<String> REQUIRED = Set.of(
            "BITTERNESS", "SWEETNESS", "AFTERTASTE", "ASTRINGENCY", "FLAVOR", "TEA_TYPE");
    private final TasteDocuments documents;

    Release normalize(String version, JsonNode value, boolean publish) {
        if (!(value instanceof ObjectNode manifest) || documents.size(value) > MAX_BYTES
                || !version.equals(manifest.path("versionCode").textValue())
                || !manifest.path("schemaVersion").isIntegralNumber()
                || !manifest.path("schemaVersion").canConvertToInt()
                || manifest.path("schemaVersion").intValue() != 1) {
            throw TasteFailure.invalid("Manifest phải có versionCode khớp URL và schemaVersion 1");
        }
        JsonNode attributes = manifest.path("attributes");
        if (!attributes.isArray() || attributes.size() > 64) {
            throw TasteFailure.invalid("Manifest phải có danh sách tối đa 64 thuộc tính");
        }
        Map<String, Attribute> definitions = new TreeMap<>();
        Map<String, ObjectNode> entries = new TreeMap<>();
        for (JsonNode item : attributes) {
            if (!(item instanceof ObjectNode attribute)) {
                throw TasteFailure.invalid("Thuộc tính taxonomy phải là JSON object");
            }
            String code = code(attribute, "attributeCode");
            labelAndOrder(attribute);
            if (!"EXACT".equals(attribute.path("matchingSemantics").textValue())) {
                throw TasteFailure.invalid("matchingSemantics phải là EXACT");
            }
            Map<String, JsonNode> values = values(attribute.path("values"));
            int maximum = integer(attribute, "maxSelections");
            String kind = attribute.path("selectionKind").asText();
            if (maximum < 1 || maximum > values.size() || switch (kind) {
                case "ORDINAL_SINGLE", "ENUM_SINGLE" -> maximum != 1;
                case "ENUM_MULTI" -> false;
                default -> true;
            }) {
                throw TasteFailure.invalid("Selection kind hoặc maxSelections không hợp lệ");
            }
            if (definitions.putIfAbsent(code, new Attribute(maximum, Set.copyOf(values.keySet()))) != null) {
                throw TasteFailure.invalid("Trùng mã thuộc tính taxonomy");
            }
            ObjectNode normalized = attribute.deepCopy();
            ArrayNode orderedValues = normalized.putArray("values");
            values.values().forEach(orderedValues::add);
            entries.put(code, normalized);
        }
        if (publish && !definitions.keySet().containsAll(REQUIRED)) {
            throw TasteFailure.invalid("Manifest phải có đủ sáu thuộc tính khẩu vị bắt buộc");
        }
        ObjectNode ordered = manifest.deepCopy();
        ArrayNode orderedAttributes = ordered.putArray("attributes");
        entries.values().forEach(orderedAttributes::add);
        JsonNode canonical = documents.canonical(ordered);
        if (documents.size(canonical) > MAX_BYTES) {
            throw TasteFailure.invalid("Manifest vượt quá 256 KiB sau chuẩn hóa");
        }
        return new Release(canonical, documents.hash(canonical), Map.copyOf(definitions));
    }

    JsonNode sensory(JsonNode value, Release release) {
        if (!value.isObject() || documents.size(value) > 16 * 1024) {
            throw TasteFailure.invalid("sensoryValues phải là JSON object tối đa 16 KiB");
        }
        ObjectNode normalized = documents.object();
        value.fields().forEachRemaining(entry -> {
            Attribute attribute = release.attributes().get(entry.getKey());
            JsonNode options = entry.getValue();
            if (attribute == null || !options.isArray() || options.isEmpty() || options.size() > attribute.maximum()) {
                throw TasteFailure.invalid("Thuộc tính hoặc số giá trị cảm quan không hợp lệ");
            }
            Set<String> codes = new HashSet<>();
            for (JsonNode option : options) {
                String code = option.textValue();
                if (!TasteDocuments.isCode(code) || !attribute.values().contains(code) || !codes.add(code)) {
                    throw TasteFailure.invalid("Mã cảm quan lạ, trùng hoặc UNKNOWN");
                }
            }
            if (codes.size() > 1 && codes.contains("NONE")) {
                throw TasteFailure.invalid("NONE không thể đi cùng giá trị cảm quan khác");
            }
            ArrayNode array = normalized.putArray(entry.getKey());
            codes.stream().sorted().forEach(array::add);
        });
        return documents.canonical(normalized);
    }

    private Map<String, JsonNode> values(JsonNode options) {
        if (!options.isArray() || options.isEmpty() || options.size() > 128) {
            throw TasteFailure.invalid("Thuộc tính phải có từ 1 đến 128 lựa chọn");
        }
        Map<String, JsonNode> values = new TreeMap<>();
        for (JsonNode value : options) {
            String code = code(value, "valueCode");
            labelAndOrder(value);
            if (values.putIfAbsent(code, value) != null) {
                throw TasteFailure.invalid("Trùng mã giá trị taxonomy");
            }
        }
        return values;
    }

    private String code(JsonNode owner, String field) {
        String value = owner.path(field).textValue();
        if (!TasteDocuments.isCode(value)) {
            throw TasteFailure.invalid("Mã taxonomy không hợp lệ");
        }
        return value;
    }

    private void labelAndOrder(JsonNode owner) {
        String label = owner.path("label").textValue();
        if (label == null || label.isBlank() || label.length() > 200 || integer(owner, "displayOrder") < 0) {
            throw TasteFailure.invalid("Thiếu label hoặc displayOrder hợp lệ");
        }
    }

    private int integer(JsonNode owner, String field) {
        JsonNode value = owner.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw TasteFailure.invalid("Trường số nguyên của manifest không hợp lệ");
        }
        return value.intValue();
    }

    record Attribute(int maximum, Set<String> values) {
    }

    record Release(JsonNode manifest, String digest, Map<String, Attribute> attributes) {
    }
}
