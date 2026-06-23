package org.hvtra.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record CategoryRequest(
        UUID parentId,
        String categoryCode,
        @NotBlank @Size(max = 255) String name,
        @NotNull Status status
) {
}

