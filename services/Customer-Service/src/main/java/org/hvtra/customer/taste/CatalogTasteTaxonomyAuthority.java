package org.hvtra.customer.taste;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.FeignException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

@Component
class CatalogTasteTaxonomyAuthority implements TasteTaxonomyAuthority {
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final int MAX_ATTRIBUTES = 64;
    private static final int MAX_VALUES = 128;

    private final CatalogTasteTaxonomyClient client;
    private final TasteJson json;
    private final ObjectMapper mapper;
    private final boolean enabled;

    CatalogTasteTaxonomyAuthority(CatalogTasteTaxonomyClient client, TasteJson json, ObjectMapper mapper,
                                  @Value("${customer.taste-survey.catalog-taxonomy-enabled:false}") boolean enabled) {
        this.client = client;
        this.json = json;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    @Override
    public Map<String, Attribute> requirePublishedRelease(String version, String sha256) {
        if (!enabled) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        CatalogTasteTaxonomyClient.Release release = fetch(version);
        if (release == null || !version.equals(release.versionCode())) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        if ("RETIRED".equals(release.state())) {
            throw TasteSurveyErrors.unsupportedTaxonomy();
        }
        if (!"PUBLISHED".equals(release.state()) || !TasteCriteria.isDigest(release.manifestSha256())) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        Document document;
        try {
            document = parseManifest(version, release.manifest());
            if (!release.manifestSha256().equals(json.sha256(json.canonical(document.manifest())))) {
                throw TasteSurveyErrors.unavailableTaxonomy();
            }
        } catch (IllegalArgumentException failure) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        if (!sha256.equals(release.manifestSha256())) {
            throw TasteSurveyErrors.conflict("TASTE_TAXONOMY_HASH_MISMATCH",
                    "Mã kiểm tra của phiên bản tiêu chí khẩu vị không khớp");
        }
        return document.attributes();
    }

    private CatalogTasteTaxonomyClient.Release fetch(String version) {
        try {
            return client.getRelease(version);
        } catch (FeignClientException failure) {
            // A missing route on an older Catalog deployment is an unavailable dependency.
            if (failure.getStatus() == 404 && isMissingRelease(failure.getResponseBody())) {
                throw TasteSurveyErrors.unsupportedTaxonomy();
            }
            throw TasteSurveyErrors.unavailableTaxonomy();
        } catch (FeignException failure) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
    }

    private boolean isMissingRelease(String body) {
        if (body == null || body.length() > 1024) {
            return false;
        }
        try {
            JsonNode error = mapper.readTree(body);
            return error != null && "TASTE_TAXONOMY_NOT_FOUND".equals(error.path("code").asText());
        } catch (JsonProcessingException failure) {
            return false;
        }
    }

    private Document parseManifest(String version, JsonNode value) {
        if (!(value instanceof ObjectNode manifest) || json.bytes(manifest).length > MAX_MANIFEST_BYTES
                || !version.equals(manifest.path("versionCode").textValue())
                || !manifest.path("schemaVersion").isIntegralNumber()
                || !manifest.path("schemaVersion").canConvertToInt() || manifest.path("schemaVersion").intValue() != 1) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        JsonNode entries = requiredArray(manifest, "attributes", MAX_ATTRIBUTES);
        Map<String, Attribute> attributes = new HashMap<>();
        ArrayNode ordered = json.array();
        for (JsonNode entry : StreamSupport.stream(entries.spliterator(), false)
                .sorted(Comparator.comparing(node -> node.path("attributeCode").asText())).toList()) {
            String code = code(entry, "attributeCode");
            JsonNode values = requiredArray(entry, "values", MAX_VALUES);
            Set<String> allowed = new HashSet<>();
            ArrayNode orderedValues = json.array();
            for (JsonNode option : StreamSupport.stream(values.spliterator(), false)
                    .sorted(Comparator.comparing(node -> node.path("valueCode").asText())).toList()) {
                if (!allowed.add(code(option, "valueCode"))) {
                    throw TasteSurveyErrors.unavailableTaxonomy();
                }
                orderedValues.add(option);
            }
            JsonNode max = entry.path("maxSelections");
            if (!max.isIntegralNumber() || !max.canConvertToInt() || max.intValue() < 1
                    || max.intValue() > allowed.size()) {
                throw TasteSurveyErrors.unavailableTaxonomy();
            }
            String kind = entry.path("selectionKind").asText();
            int limit = switch (kind) {
                case "ORDINAL_SINGLE", "ENUM_SINGLE" -> 1;
                case "ENUM_MULTI" -> max.intValue();
                default -> throw TasteSurveyErrors.unavailableTaxonomy();
            };
            if (limit != max.intValue() || attributes.putIfAbsent(code, new Attribute(limit, Set.copyOf(allowed))) != null) {
                throw TasteSurveyErrors.unavailableTaxonomy();
            }
            ObjectNode attribute = entry.deepCopy();
            attribute.set("values", orderedValues);
            ordered.add(attribute);
        }
        ObjectNode canonicalManifest = manifest.deepCopy();
        canonicalManifest.set("attributes", ordered);
        return new Document(canonicalManifest, Map.copyOf(attributes));
    }

    private JsonNode requiredArray(JsonNode owner, String field, int max) {
        JsonNode value = owner.path(field);
        if (!value.isArray() || value.isEmpty() || value.size() > max) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        return value;
    }

    private String code(JsonNode owner, String field) {
        String value = owner.path(field).textValue();
        if (!owner.isObject() || !TasteCriteria.isCode(value)) {
            throw TasteSurveyErrors.unavailableTaxonomy();
        }
        return value;
    }

    private record Document(ObjectNode manifest, Map<String, Attribute> attributes) {
    }
}
