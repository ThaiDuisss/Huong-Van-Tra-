package org.hvtra.promotionservice.dto.event;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.math.BigDecimal;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderPromotionEventPayload implements CommonEventPayload {
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
    List<String> usageIds;
    String causationId;
}
