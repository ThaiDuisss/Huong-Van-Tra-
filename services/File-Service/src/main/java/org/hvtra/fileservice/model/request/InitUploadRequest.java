package org.hvtra.fileservice.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record InitUploadRequest(
        @NotBlank String originalFilename,
        @NotBlank String contentType,
        @NotNull @Positive Long sizeBytes,
        String refType,
        UUID refId,
        String usageType
) {}
