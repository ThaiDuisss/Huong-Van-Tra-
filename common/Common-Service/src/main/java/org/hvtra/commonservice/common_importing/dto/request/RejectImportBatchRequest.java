package org.hvtra.commonservice.common_importing.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectImportBatchRequest(
        @NotBlank(message = "reason is required")
        String reason,
        Long expectedVersion
) {
}
