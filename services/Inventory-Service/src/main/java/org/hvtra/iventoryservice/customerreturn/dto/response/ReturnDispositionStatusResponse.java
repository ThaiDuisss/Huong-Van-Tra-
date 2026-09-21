package org.hvtra.iventoryservice.customerreturn.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ReturnDispositionStatusResponse(
        UUID commandId,
        ReturnDispositionReconciliationStatus status,
        UUID returnId,
        UUID inspectionId,
        String failureCode,
        String failureReason,
        Instant processedAt
) {}
