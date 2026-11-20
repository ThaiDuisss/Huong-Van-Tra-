package org.hvtra.orderservice.outbox.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Component
public class OrderKafkaSigner {
    private static final String POLICY = "hvtra-order-kafka-v1";
    private static final String KEY_TOKEN_PATTERN = "[A-Za-z0-9_.-]{1,64}";
    private final ObjectMapper mapper;
    private final String keyId;
    private final String keyVersion;
    private final String encodedSecret;

    public OrderKafkaSigner(
            ObjectMapper mapper,
            @Value("${ORDER_KAFKA_SIGNING_KEY_ID:}") String keyId,
            @Value("${ORDER_KAFKA_SIGNING_KEY_VERSION:}") String keyVersion,
            @Value("${ORDER_KAFKA_SIGNING_SECRET_B64:}") String encodedSecret
    ) {
        this.mapper = mapper;
        this.keyId = keyId;
        this.keyVersion = keyVersion;
        this.encodedSecret = encodedSecret;
    }

    public void sign(String topic, String recordKey, Map<String, Object> payload) {
        if (!keyId.matches(KEY_TOKEN_PATTERN) || !keyVersion.matches(KEY_TOKEN_PATTERN)
                || payload.containsKey("kafkaAuth")) {
            throw new IllegalStateException("Invalid Kafka signing configuration");
        }
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Kafka signing key encoding");
        }
        try {
            if (secret.length < 32) {
                throw new IllegalStateException("Kafka signing key is missing or too short");
            }
            JsonNode business = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(mapper.writeValueAsBytes(payload));
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            for (String part : List.of(POLICY, topic, recordKey, keyId, keyVersion)) {
                writeText(frame, 's', part);
            }
            writeNode(frame, business);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            payload.put("kafkaAuth", Map.of("keyId", keyId, "keyVersion", keyVersion,
                    "signature", HexFormat.of().formatHex(mac.doFinal(frame.toByteArray()))));
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Kafka event signing failed", exception);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static void writeNode(ByteArrayOutputStream frame, JsonNode node) {
        if (node.isNull()) {
            frame.write('n');
        } else if (node.isBoolean()) {
            frame.write(node.booleanValue() ? 't' : 'f');
        } else if (node.isTextual()) {
            writeText(frame, 's', node.textValue());
        } else if (node.isNumber()) {
            var number = node.decimalValue().stripTrailingZeros();
            if (Math.abs((long) number.scale()) > 32 || number.precision() > 32) {
                throw new IllegalStateException("Kafka signing number exceeds bounds");
            }
            writeText(frame, 'd', number.toPlainString());
        } else if (node.isArray()) {
            writeCount(frame, 'a', node.size());
            node.forEach(value -> writeNode(frame, value));
        } else if (node.isObject()) {
            List<String> keys = new ArrayList<>();
            node.fieldNames().forEachRemaining(keys::add);
            keys.sort((left, right) -> Arrays.compareUnsigned(utf8(left), utf8(right)));
            writeCount(frame, 'o', keys.size());
            for (String key : keys) {
                writeText(frame, 's', key);
                writeNode(frame, node.get(key));
            }
        } else {
            throw new IllegalStateException("Unsupported Kafka signing JSON value");
        }
    }

    private static byte[] utf8(String value) {
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) {
            throw new IllegalStateException("Invalid Kafka signing Unicode value");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeText(ByteArrayOutputStream frame, char tag, String value) {
        byte[] encoded = utf8(value);
        writeCount(frame, tag, encoded.length);
        frame.writeBytes(encoded);
    }

    private static void writeCount(ByteArrayOutputStream frame, char tag, int value) {
        frame.write(tag);
        frame.writeBytes(Integer.toString(value).getBytes(StandardCharsets.US_ASCII));
        frame.write(':');
    }
}
