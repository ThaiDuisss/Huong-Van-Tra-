package org.hvtra.orderservice.outbox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderKafkaSignerTest {
    private static final String ORDER_ID = "550e8400-e29b-41d4-a716-446655440001";

    static OrderKafkaSigner signer() {
        return signer(new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    static OrderKafkaSigner signer(ObjectMapper mapper) {
        byte[] fixtureKey = new byte[32];
        for (int index = 0; index < fixtureKey.length; index++) {
            fixtureKey[index] = (byte) index;
        }
        return new OrderKafkaSigner(mapper, "test-order", "1", Base64.getEncoder().encodeToString(fixtureKey));
    }

    @Test
    void matchesPythonVerifierCanonicalFrame() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> payload = mapper.readValue(
                "{\"z\":[false,null,1.2300,-0.000],\"a\":\"Tr\\u00e0 \\ud83c\\udf75\"}", new TypeReference<>() {});
        signer(mapper).sign("order.completed", ORDER_ID, payload);
        assertEquals(Map.of("keyId", "test-order", "keyVersion", "1", "signature",
                "3cd1522b5cc5d4759f356af55905c0f5db271a7aebb4dba727f8fe9cb2ba224b"), payload.get("kafkaAuth"));
    }

    @Test
    void refusesMissingKafkaKeyConfiguration() {
        Map<String, Object> payload = new HashMap<>();
        assertThrows(IllegalStateException.class,
                () -> new OrderKafkaSigner(new ObjectMapper(), "", "", "").sign("order.completed", ORDER_ID, payload));
    }
}
