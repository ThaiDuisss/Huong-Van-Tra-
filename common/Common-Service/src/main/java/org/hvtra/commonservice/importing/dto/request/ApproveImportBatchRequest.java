package org.hvtra.commonservice.importing.dto.request;

public record ApproveImportBatchRequest(
        String note,
        Long expectedVersion
) {
}
