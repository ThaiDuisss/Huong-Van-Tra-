package org.hvtra.catalog.importing.commercial;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.CategoryInput;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.ProductInput;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.Row;
import static org.hvtra.catalog.importing.commercial.CommercialImportPlan.VariantInput;

@Component
public class CommercialImportCodec {
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

    public CommercialImportPlan parse(String json) {
        if (json == null || json.length() > 5_000_000) {
            throw new IllegalArgumentException("IMPORT_JSON_MISSING_OR_TOO_LARGE");
        }
        try {
            CommercialImportPlan plan = mapper.readValue(json, CommercialImportPlan.class);
            if (plan == null) throw new IllegalArgumentException("IMPORT_PLAN_REQUIRED");
            return plan;
        } catch (JsonProcessingException | NullPointerException exception) {
            // Do not echo source JSON, authorization headers or Jackson's input excerpt.
            throw new IllegalArgumentException("IMPORT_JSON_INVALID");
        }
    }

    public CommercialImportDecisions parseDecisions(String json) {
        if (json == null || json.length() > 5_000_000) throw new IllegalArgumentException("DECISIONS_JSON_MISSING_OR_TOO_LARGE");
        try {
            CommercialImportDecisions decisions = mapper.readValue(json, CommercialImportDecisions.class);
            if (decisions == null) throw new IllegalArgumentException("COMMERCIAL_DECISIONS_REQUIRED");
            return decisions;
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new IllegalArgumentException("COMMERCIAL_DECISIONS_JSON_INVALID");
        }
    }

    public String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(value)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("IMPORT_HASH_UNAVAILABLE", exception);
        }
    }

    public String rowHash(Row row) { return hash(normalize(payload(row))); }

    public List<Map<String, Object>> actions(CommercialImportPlan plan) {
        return plan.rows().stream().map(row -> {
            Map<String, Object> action = new TreeMap<>();
            action.put("entity", row.kind().name());
            action.put("sourceId", row.id());
            action.put("operation", "CREATE_OR_SKIP_IDENTICAL_OWNED_ROW");
            action.put("payloadHash", rowHash(row));
            return action;
        }).toList();
    }

    /** Fixed application-owned columns; no SQL identifiers are accepted from JSON. */
    public Map<String, Object> payload(Row row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", bytes(row.id()));
        values.put(row.kind().codeColumn, row.code());
        values.put(row.kind().nameColumn, row.name());
        values.put("status", row.status() == null ? null : row.status().name());
        switch (row) {
            case CategoryInput c -> {
                values.put("parent_id", bytes(c.parentId()));
                values.put("default_reorder_point", c.defaultReorderPoint());
            }
            case ProductInput p -> {
                values.put("category_id", bytes(p.categoryId()));
                values.put("description", p.description());
                values.put("image_url", p.imageUrl());
            }
            case VariantInput v -> {
                values.put("product_id", bytes(v.productId()));
                values.put("selling_price", v.sellingPrice());
                values.put("unit_id", bytes(v.unitId()));
                values.put("quantity", v.quantity());
                values.put("cost_price", v.costPrice());
                values.put("inventory_policy", v.inventoryPolicy() == null ? null : v.inventoryPolicy().name());
                values.put("barcode", v.barcode());
            }
        }
        return values;
    }

    public static byte[] bytes(UUID id) {
        return id == null ? null : ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    public static UUID uuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) throw new IllegalArgumentException("INVALID_BINARY_UUID");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof byte[] bytes) return HexFormat.of().formatHex(bytes);
        if (value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((key, item) -> normalized.put(key.toString().toLowerCase(Locale.ROOT), normalize(item)));
            return normalized;
        }
        if (value instanceof List<?> list) return list.stream().map(CommercialImportCodec::normalize).toList();
        return value.toString();
    }
}
