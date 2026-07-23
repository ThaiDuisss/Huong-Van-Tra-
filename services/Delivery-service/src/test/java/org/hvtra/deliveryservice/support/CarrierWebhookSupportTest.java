package org.hvtra.deliveryservice.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.deliveryservice.carrier.support.CarrierWebhookSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierWebhookSupportTest {

    private final CarrierWebhookSupport support = new CarrierWebhookSupport();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void removesOnlyAhamoveMultiStopCallbackSuffix() {
        assertThat(support.ahamoveBaseOrderId("24ABCDEF-2")).isEqualTo("24ABCDEF");
        assertThat(support.ahamoveBaseOrderId("24ABCDEF")).isEqualTo("24ABCDEF");
    }

    @Test
    void readsTrackingNumberAndLastDeliveryPoint() throws Exception {
        JsonNode payload = objectMapper.readTree("""
                {
                  "path": [
                    {"address": "Pickup"},
                    {"tracking_number": "SHP-001", "status": "COMPLETED"}
                  ]
                }
                """);

        assertThat(support.ahamoveTrackingNumber(payload)).isEqualTo("SHP-001");
        assertThat(support.text(support.lastDeliveryPath(payload), "status")).isEqualTo("COMPLETED");
    }

    @Test
    void createsStableDeduplicationKey() {
        String first = support.deduplicationKey("ORDER-1", "COMPLETED", 123);
        String second = support.deduplicationKey("ORDER-1", "COMPLETED", 123);

        assertThat(first).hasSize(64).isEqualTo(second);
    }
}
