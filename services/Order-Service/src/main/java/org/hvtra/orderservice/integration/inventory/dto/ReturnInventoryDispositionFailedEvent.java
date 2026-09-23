package org.hvtra.orderservice.integration.inventory.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.kafka.event.CommonEventPayload;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnInventoryDispositionFailedEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String commandId;
    String returnId;
    String failureCode;
    String failureReason;
    String failedAt;
}
