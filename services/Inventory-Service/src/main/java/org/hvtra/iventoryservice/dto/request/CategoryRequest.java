package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.enums.Status;

public record CategoryRequest(
        Long parentId,
        @NotBlank @Size(max = 255) String name,
        @NotNull Status status
) {
}
