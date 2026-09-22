package org.hvtra.commonservice.importing.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectImportBatchRequest(
        @NotBlank(message = "reason is required")
        String reason,
        Long expectedVersion
) {
}
