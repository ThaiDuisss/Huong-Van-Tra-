package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.iventoryservice.enums.Status;

public record ProductRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 255) String name,
        String description,
        @Size(max = 500) String imageUrl,
        @NotNull Status status
) {
}
