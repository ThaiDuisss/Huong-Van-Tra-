package org.hvtra.commonservice.common_importing.dto.response;

import org.hvtra.commonservice.common_importing.enums.ImportBatchStatus;
import org.hvtra.commonservice.common_importing.enums.ImportType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        ImportType importType,
        String batchCode,
        ImportBatchStatus status,
        Long version,
        String sourceFileName,
        String note,
        UUID createdBy,
        UUID submittedBy,
        Instant submittedAt,
        UUID approvedBy,
        Instant approvedAt,
        UUID rejectedBy,
        Instant rejectedAt,
        String rejectionReason,
        List<ImportBatchItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
