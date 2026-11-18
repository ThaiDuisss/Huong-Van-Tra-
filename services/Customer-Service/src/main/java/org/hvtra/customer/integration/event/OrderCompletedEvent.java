package org.hvtra.customer.integration.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Payload ORDER_COMPLETED (topic order.completed) do Order-Service phát qua outbox. Chỉ lấy các trường cần dùng. */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderCompletedEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    UUID orderId;
    String orderCode;
    UUID customerId;
    BigDecimal finalAmount;
    Instant completedAt;
    Instant occurredAt;
}
