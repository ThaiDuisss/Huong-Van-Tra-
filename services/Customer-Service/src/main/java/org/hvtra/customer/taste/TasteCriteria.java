package org.hvtra.customer.taste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
class TasteCriteria {
    private static final int MAX_BYTES = 16 * 1024;
    private static final int MAX_GROUPS = 32;
    private static final int MAX_VALUES = 32;
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    private final TasteJson json;

    static boolean isCode(String value) {
        return value != null && !"UNKNOWN".equals(value) && CODE.matcher(value).matches();
    }

    static boolean isDigest(String value) {
        return value != null && DIGEST.matcher(value).matches();
    }

    Selection normalize(JsonNode criteria, boolean allowMissing) {
        JsonNode value = allowMissing && (criteria == null || criteria.isNull()) ? json.object() : criteria;
        if (value == null || !value.isObject() || json.bytes(value).length > MAX_BYTES) {
            throw invalid("Tiêu chí khẩu vị phải là một JSON object tối đa 16 KiB");
        }
        onlyFields(value, Set.of("hardGroups", "softGroups"));
        SortedMap<String, Set<String>> hard = parseHard(groups(value, "hardGroups"));
        SortedMap<String, SortedMap<String, Integer>> soft = parseSoft(groups(value, "softGroups"));
        soft.forEach((attribute, priorities) -> {
            if (hard.containsKey(attribute) && !hard.get(attribute).containsAll(priorities.keySet())) {
                throw invalid("Giá trị ưu tiên phải thuộc nhóm bắt buộc của cùng thuộc tính");
            }
        });
        ObjectNode canonical = toJson(hard, soft);
        if (json.bytes(canonical).length > MAX_BYTES) {
            throw invalid("Tiêu chí khẩu vị sau chuẩn hóa vượt quá 16 KiB");
        }
        return new Selection(hard, soft, canonical);
    }

    void validate(Selection criteria, Map<String, TasteTaxonomyAuthority.Attribute> attributes) {
        criteria.hard().forEach((code, values) -> validateGroup(attributes, code, values));
        criteria.soft().forEach((code, values) -> validateGroup(attributes, code, values.keySet()));
    }

    String digest(String taxonomySha256, Selection criteria) {
        ObjectNode value = json.object();
        value.set("criteria", criteria.document());
        value.put("taxonomySha256", taxonomySha256);
        return json.sha256(value);
    }

    private SortedMap<String, Set<String>> parseHard(JsonNode groups) {
        SortedMap<String, Set<String>> result = new TreeMap<>();
        for (JsonNode group : groups) {
            onlyFields(group, Set.of("attribute", "allowedValues"));
            String code = requireCode(group.path("attribute"));
            Set<String> values = new TreeSet<>();
            for (JsonNode value : groupValues(group, "allowedValues")) {
                if (!values.add(requireCode(value))) {
                    throw invalid("Giá trị bắt buộc bị trùng");
                }
            }
            validateNone(values);
            if (result.putIfAbsent(code, values) != null) {
                throw invalid("Nhóm tiêu chí bắt buộc bị trùng");
            }
        }
        return result;
    }

    private SortedMap<String, SortedMap<String, Integer>> parseSoft(JsonNode groups) {
        SortedMap<String, SortedMap<String, Integer>> result = new TreeMap<>();
        for (JsonNode group : groups) {
            onlyFields(group, Set.of("attribute", "priorities"));
            String code = requireCode(group.path("attribute"));
            SortedMap<String, Integer> values = new TreeMap<>();
            for (JsonNode priority : groupValues(group, "priorities")) {
                onlyFields(priority, Set.of("value", "weight"));
                String option = requireCode(priority.path("value"));
                JsonNode weight = priority.path("weight");
                if (!weight.isIntegralNumber() || !weight.canConvertToInt()
                        || weight.intValue() < 1 || weight.intValue() > 100) {
                    throw invalid("Trọng số ưu tiên phải là số nguyên từ 1 đến 100");
                }
                if (values.putIfAbsent(option, weight.intValue()) != null) {
                    throw invalid("Giá trị ưu tiên bị trùng");
                }
            }
            validateNone(values.keySet());
            if (result.putIfAbsent(code, values) != null) {
                throw invalid("Nhóm ưu tiên bị trùng");
            }
        }
        return result;
    }

    private ObjectNode toJson(SortedMap<String, Set<String>> hard, SortedMap<String, SortedMap<String, Integer>> soft) {
        ObjectNode document = json.object();
        ArrayNode hardGroups = document.putArray("hardGroups");
        hard.forEach((attribute, values) -> {
            ObjectNode group = hardGroups.addObject();
            ArrayNode allowedValues = group.putArray("allowedValues");
            values.forEach(allowedValues::add);
            group.put("attribute", attribute);
        });
        ArrayNode softGroups = document.putArray("softGroups");
        soft.forEach((attribute, values) -> {
            ObjectNode group = softGroups.addObject();
            group.put("attribute", attribute);
            ArrayNode priorities = group.putArray("priorities");
            values.forEach((value, weight) -> priorities.addObject().put("value", value).put("weight", weight));
        });
        return document;
    }

    private void validateGroup(Map<String, TasteTaxonomyAuthority.Attribute> attributes, String code, Set<String> values) {
        TasteTaxonomyAuthority.Attribute attribute = attributes.get(code);
        if (attribute == null || !attribute.values().containsAll(values)) {
            throw invalid("Thuộc tính hoặc giá trị không thuộc phiên bản tiêu chí đã chọn");
        }
        if (values.size() > attribute.maxSelections()) {
            throw invalid("Số lựa chọn vượt giới hạn của thuộc tính");
        }
    }

    private void validateNone(Set<String> values) {
        if (values.contains("NONE") && values.size() > 1) {
            throw invalid("Không thể chọn NONE cùng giá trị khác trong một nhóm");
        }
    }

    private JsonNode groups(JsonNode parent, String field) {
        JsonNode value = parent.has(field) ? parent.get(field) : json.array();
        if (!value.isArray() || value.size() > MAX_GROUPS) {
            throw invalid("Mỗi loại tiêu chí phải là danh sách tối đa 32 nhóm");
        }
        return value;
    }

    private JsonNode groupValues(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isArray() || value.isEmpty() || value.size() > MAX_VALUES) {
            throw invalid("Mỗi nhóm phải có từ 1 đến 32 lựa chọn");
        }
        return value;
    }

    private void onlyFields(JsonNode value, Set<String> allowed) {
        if (!value.isObject()) {
            throw invalid("Nhóm tiêu chí phải là JSON object");
        }
        Set<String> fields = new HashSet<>();
        value.fieldNames().forEachRemaining(fields::add);
        if (!allowed.containsAll(fields)) {
            throw invalid("Tiêu chí chứa trường không được hỗ trợ");
        }
    }

    private String requireCode(JsonNode node) {
        String code = node.textValue();
        if (!isCode(code)) {
            throw invalid("Mã thuộc tính hoặc giá trị không hợp lệ");
        }
        return code;
    }

    private RuntimeException invalid(String message) {
        return TasteSurveyErrors.invalidCriteria(message);
    }

    record Selection(
            SortedMap<String, Set<String>> hard,
            SortedMap<String, SortedMap<String, Integer>> soft,
            ObjectNode document
    ) {
        boolean isEmpty() {
            return hard.isEmpty() && soft.isEmpty();
        }
    }
}
