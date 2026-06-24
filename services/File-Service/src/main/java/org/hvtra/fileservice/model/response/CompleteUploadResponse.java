package org.hvtra.fileservice.model.response;

import org.hvtra.fileservice.model.enums.FileVisibility;

import java.util.UUID;

public record CompleteUploadResponse(
        UUID fileId,
        FileVisibility visibility,
        String publicUrl
) {
}
