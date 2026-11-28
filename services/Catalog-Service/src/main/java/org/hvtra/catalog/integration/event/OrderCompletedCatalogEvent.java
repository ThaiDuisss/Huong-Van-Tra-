package org.hvtra.catalog.integration.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Payload ORDER_COMPLETED từ Order-Service outbox để lưu Product/Variant/BOM CUSTOMER khi hoàn tất đơn bundle. */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCompletedCatalogEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    UUID orderId;
    String orderCode;
    UUID customerId;
    String orderStatus;
    String paymentStatus;
    Instant completedAt;
    Instant occurredAt;
    List<OrderItemPayload> items;

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItemPayload {
        UUID orderItemId;
        String purchaseType;
        String itemType;
        UUID productVariantId;
        String sku;
        String productName;
        String variantName;
        BigDecimal quantity;
        BigDecimal unitPrice;
        List<ComponentPayload> components;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentPayload {
        UUID ingredientId;
        String ingredientCode;
        BigDecimal quantity;
        String unit;
    }
}
