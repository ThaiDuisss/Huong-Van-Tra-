package org.hvtra.identityservice.account.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeAccountStatusRequest(
        @NotNull(message = "Thiếu trạng thái enabled")
        Boolean enabled
) {
}
