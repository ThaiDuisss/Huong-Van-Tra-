package org.hvtra.fileservice.model.response;

import java.util.UUID;

public record InitUploadResponse(
        UUID fileId,
        String objectKey,
        String uploadUrl,
        Integer expiresInSeconds
) {}