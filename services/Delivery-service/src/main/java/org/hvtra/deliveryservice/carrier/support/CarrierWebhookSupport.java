package org.hvtra.deliveryservice.carrier.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CarrierWebhookSupport {

    private static final Pattern AHAMOVE_STOP_SUFFIX = Pattern.compile("^(.+)-([1-9][0-9]*)$");

    public String deduplicationKey(Object... components) {
        List<String> values = new ArrayList<>(components.length);
        for (Object component : components) {
            values.add(component == null ? "" : String.valueOf(component));
        }
        return sha256(String.join("|", values));
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot calculate SHA-256", exception);
        }
    }

    public boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    public Map<String, List<String>> toHeaderMap(HttpHeaders headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return result;
    }

    public String text(JsonNode payload, String... names) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = payload.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    public Long longValue(JsonNode payload, String... names) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = payload.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.longValue();
            }
            try {
                return Long.valueOf(value.asText());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public Instant parseIsoOrNow(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }
//chuyển một giá trị thời gian Unix Epoch tính theo giây thành Instant
    //1665742305 → một thời điểm trong năm 2022
    public Instant epochSeconds(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull() || !value.isNumber()) {
                continue;
            }
            double seconds = value.asDouble();
            long wholeSeconds = (long) seconds;
            long nanos = Math.round((seconds - wholeSeconds) * 1_000_000_000D);
            if (nanos >= 1_000_000_000L) {
                wholeSeconds++;
                nanos = 0;
            }
            return Instant.ofEpochSecond(wholeSeconds, Math.max(0, nanos));
        }
        return null;
    }

    public String ahamoveBaseOrderId(String callbackId) {
        if (callbackId == null || callbackId.isBlank()) {
            return callbackId;
        }
        Matcher matcher = AHAMOVE_STOP_SUFFIX.matcher(callbackId.trim());
        return matcher.matches() ? matcher.group(1) : callbackId.trim();
    }

    public String ahamoveTrackingNumber(JsonNode payload) {
        JsonNode path = payload == null ? null : payload.get("path");
        if (path == null || !path.isArray()) {
            return null;
        }
        for (int index = 1; index < path.size(); index++) {
            String tracking = text(path.get(index), "tracking_number");
            if (tracking != null) {
                return tracking;
            }
        }
        return null;
    }

    public JsonNode lastDeliveryPath(JsonNode payload) {
        JsonNode path = payload == null ? null : payload.get("path");
        if (path == null || !path.isArray() || path.size() < 2) {
            return null;
        }
        return path.get(path.size() - 1);
    }
}
