package org.hvtra.iventoryservice.integration.order.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.kafka.event.CommonEventPayload;

import java.math.BigDecimal;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderInventoryEventPayload implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String orderId;
    String orderCode;
    String customerId;
    String paymentStatus;
    String orderStatus;
    String paymentMethod;
    String paymentCode;
    BigDecimal finalAmount;
    BigDecimal paidAmount;
    BigDecimal discountAmount;
    String causationId;
}
