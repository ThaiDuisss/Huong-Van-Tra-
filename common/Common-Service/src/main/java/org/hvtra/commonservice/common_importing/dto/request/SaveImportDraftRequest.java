package org.hvtra.commonservice.common_importing.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hvtra.commonservice.common_importing.enums.ImportType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SaveImportDraftRequest(
        @NotNull(message = "Loại import không được để trống")
        ImportType type,

        String sourceFileName,

        UUID fileId,

        @NotEmpty(message = "Danh sách dòng không được để trống")
        List<DraftItem> items
) {
    public record DraftItem(
            Integer rowIndex,
            Map<String, Object> payload,
            Integer occurrenceCount
    ) {
    }
}
