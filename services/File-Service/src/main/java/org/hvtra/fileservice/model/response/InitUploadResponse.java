package org.hvtra.fileservice.model.response;

import org.hvtra.fileservice.model.enums.FileVisibility;

import java.util.UUID;

public record InitUploadResponse(
        UUID fileId,
        FileVisibility visibility,
        String bucket,
        String objectKey,
        String uploadUrl,
        String publicUrl,
        Integer expiresInSeconds
) {
}