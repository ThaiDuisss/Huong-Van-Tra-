package org.hvtra.orderservice.integration.inventory.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.kafka.event.CommonEventPayload;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnInventoryDispositionProcessedEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String commandId;
    String returnId;
    String inspectionId;
    String processedAt;
}
