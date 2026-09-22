package org.hvtra.commonservice.common_importing.dto.request;

public record ApproveImportBatchRequest(
        String note,
        Long expectedVersion
) {
}
