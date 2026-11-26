package org.hvtra.commonservice.common_core.taste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validation shared by Catalog and Customer. Canonicalization and authority remain with the callers. */
public final class TasteManifestV2 {
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,39}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Set<String> RESERVED = Set.of("UNKNOWN", "UNANSWERED", "NOT_APPLICABLE");
    private static final Set<String> MANIFEST_FIELDS = Set.of("schemaVersion", "versionCode", "attributes", "compatibility");
    private static final Set<String> ATTRIBUTE_FIELDS = Set.of("attributeCode", "label", "displayOrder", "selectionKind",
            "matchingSemantics", "maxSelections", "allowsNotApplicable", "values", "definition");
    private static final Set<String> VALUE_FIELDS = Set.of("valueCode", "label", "displayOrder", "definition");

    private TasteManifestV2() {
    }

    public static Reference validate(JsonNode manifest, boolean published) {
        onlyFields(manifest, MANIFEST_FIELDS);
        String version = manifest.path("versionCode").asText("");
        require(manifest.path("schemaVersion").isIntegralNumber() && manifest.path("schemaVersion").intValue() == 2
                && manifest.path("schemaVersion").canConvertToInt() && manifest.path("versionCode").isTextual()
                && VERSION.matcher(version).matches() && !"current".equalsIgnoreCase(version));
        JsonNode attributes = manifest.path("attributes");
        require(attributes.isArray() && attributes.size() <= 32 && (!published || !attributes.isEmpty()));
        Set<String> codes = new HashSet<>();
        for (JsonNode attribute : attributes) {
            onlyFields(attribute, ATTRIBUTE_FIELDS);
            require(codes.add(code(attribute, "attributeCode")));
            labelAndOrder(attribute);
            require("EXACT".equals(attribute.path("matchingSemantics").textValue()));
            require(!attribute.has("allowsNotApplicable") || attribute.get("allowsNotApplicable").isBoolean());
            JsonNode options = attribute.path("values");
            require(options.isArray() && !options.isEmpty() && options.size() <= 128);
            JsonNode maximum = attribute.path("maxSelections");
            require(maximum.isIntegralNumber() && maximum.canConvertToInt() && maximum.intValue() >= 1
                    && maximum.intValue() <= Math.min(options.size(), 32));
            String kind = attribute.path("selectionKind").asText("");
            require("ENUM_MULTI".equals(kind)
                    || ("ORDINAL_SINGLE".equals(kind) || "ENUM_SINGLE".equals(kind)) && maximum.intValue() == 1);
            Set<String> valueCodes = new HashSet<>();
            for (JsonNode option : options) {
                onlyFields(option, VALUE_FIELDS);
                require(valueCodes.add(code(option, "valueCode")));
                labelAndOrder(option);
            }
        }
        if (!manifest.has("compatibility")) {
            return null;
        }
        JsonNode reference = manifest.get("compatibility");
        onlyFields(reference, Set.of("sourceVersionCode", "sourceManifestSha256"));
        String source = reference.path("sourceVersionCode").asText("");
        String digest = reference.path("sourceManifestSha256").asText("");
        require(reference.path("sourceVersionCode").isTextual() && reference.path("sourceManifestSha256").isTextual()
                && VERSION.matcher(source).matches() && !"current".equalsIgnoreCase(source)
                && !version.equals(source) && DIGEST.matcher(digest).matches());
        return new Reference(source, digest);
    }

    public static boolean isAdditive(JsonNode source, JsonNode target) {
        Map<String, JsonNode> targetAttributes = indexed(target.path("attributes"), "attributeCode");
        for (JsonNode before : source.path("attributes")) {
            JsonNode after = targetAttributes.get(before.path("attributeCode").textValue());
            if (after == null || !semanticFields(before, true).equals(semanticFields(after, true))) {
                return false;
            }
            Map<String, JsonNode> targetValues = indexed(after.path("values"), "valueCode");
            for (JsonNode value : before.path("values")) {
                JsonNode retained = targetValues.get(value.path("valueCode").textValue());
                if (retained == null || !semanticFields(value, false).equals(semanticFields(retained, false))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ObjectNode semanticFields(JsonNode value, boolean attribute) {
        ObjectNode fields = value.deepCopy();
        fields.remove("displayOrder");
        if (attribute) {
            fields.remove("values");
            if (!fields.path("allowsNotApplicable").booleanValue()) {
                fields.remove("allowsNotApplicable");
            }
        }
        return fields;
    }

    private static Map<String, JsonNode> indexed(JsonNode rows, String key) {
        Map<String, JsonNode> index = new HashMap<>();
        rows.forEach(row -> index.put(row.path(key).textValue(), row));
        return index;
    }

    private static void onlyFields(JsonNode value, Set<String> allowed) {
        require(value.isObject());
        value.fieldNames().forEachRemaining(field -> require(allowed.contains(field)));
    }

    private static String code(JsonNode node, String field) {
        String value = node.path(field).textValue();
        require(value != null && CODE.matcher(value).matches() && !RESERVED.contains(value));
        return value;
    }

    private static void labelAndOrder(JsonNode node) {
        text(node.path("label"), 200);
        JsonNode order = node.path("displayOrder");
        require(order.isIntegralNumber() && order.canConvertToInt() && order.intValue() >= 0);
        if (node.has("definition")) {
            text(node.path("definition"), 1000);
        }
    }

    private static void text(JsonNode value, int max) {
        require(value.isTextual() && !value.textValue().isBlank() && value.textValue().length() <= max);
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("Manifest động không đúng hợp đồng schemaVersion 2");
        }
    }

    public record Reference(String sourceVersionCode, String sourceManifestSha256) {
    }
}
