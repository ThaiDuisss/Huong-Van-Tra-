package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.OrderReturnHistoryAction;
import org.hvtra.orderservice.domain.enums.ReturnStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OrderReturnTimelineResponse {
    private UUID id;
    private OrderReturnHistoryAction action;
    private ReturnStatus fromStatus;
    private ReturnStatus toStatus;
    private UUID actorUserId;
    private String note;
    private String referenceType;
    private String referenceId;
    private Instant occurredAt;
}
