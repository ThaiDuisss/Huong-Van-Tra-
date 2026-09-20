package org.hvtra.iventoryservice.disposal.dto.response;

import org.hvtra.iventoryservice.domain.enums.DisposalRequestStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DisposalRequestResponse(
        UUID id,
        String requestCode,
        String sourceType,
        UUID sourceInspectionId,
        String inspectionCode,
        UUID returnId,
        String returnCode,
        UUID branchId,
        DisposalRequestStatus status,
        UUID requestedBy,
        Instant requestedAt,
        UUID approvedBy,
        Instant approvedAt,
        UUID transactionId,
        String note,
        List<DisposalRequestLineResponse> lines
) {
}
