package org.hvtra.notificationservice.dto.event;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

import java.time.Instant;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderNotificationEventPayload implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String orderId;
    String orderCode;
    String customerId;
    String userId;
    String paymentId;
    String paymentCode;
    String oldStatus;
    String newStatus;
    String title;
    String message;
    String sagaId;
    String correlationId;
    String causationId;
    Instant occurredAt;
}
