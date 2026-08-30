package org.hvtra.iventoryservice.document.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectReceiptRequest(
        @NotBlank(message = "Rejection reason is required")
        String rejectionReason
) {
}
