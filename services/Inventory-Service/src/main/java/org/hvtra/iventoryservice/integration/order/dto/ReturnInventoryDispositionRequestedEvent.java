package org.hvtra.iventoryservice.integration.order.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.kafka.event.CommonEventPayload;

import java.util.ArrayList;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReturnInventoryDispositionRequestedEvent implements CommonEventPayload {
    String eventId;
    String eventType;
    String eventKey;
    String commandId;
    String returnId;
    String returnCode;
    String orderId;
    String orderCode;
    String branchId;
    String status;
    String requestedBy;
    String occurredAt;
    String sagaId;
    String correlationId;
    String causationId;
    List<ReturnInventoryDispositionRequestedLine> lines = new ArrayList<>();
}
