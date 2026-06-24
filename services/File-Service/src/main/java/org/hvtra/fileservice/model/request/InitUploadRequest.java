package org.hvtra.fileservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hvtra.fileservice.model.enums.FilePurpose;

import java.util.UUID;

public record InitUploadRequest(
        @NotBlank String originalFilename,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes,
        @NotNull FilePurpose purpose,

        String ownerType,
        UUID ownerId,

        String refType,
        UUID refId
) {
}
